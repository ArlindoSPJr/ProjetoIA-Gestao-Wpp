package app.gestaoIAWpp.agente;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Agente de atendimento com o Claude e function calling manual.
 *
 * <p>Cada turno é um loop: envia a conversa ao modelo; se ele pedir tools ({@code tool_use}), o Java
 * executa cada uma e devolve os resultados ({@code tool_result}) numa nova requisição; quando o modelo
 * termina ({@code end_turn}), o texto final vai ao cliente. O limite {@code agente.max-iteracoes} conta
 * chamadas à API por turno (uma resposta pode trazer vários {@code tool_use}).
 */
@Service
public class AgenteClaude implements AgenteAtendimento {

    static final String MENSAGEM_FALLBACK =
            "Desculpe, não consegui concluir agora. Vou chamar um atendente para te ajudar.";

    private static final Logger log = LoggerFactory.getLogger(AgenteClaude.class);
    private static final long MAX_TOKENS = 16000L;
    private static final String CAMINHO_PROMPT = "prompts/sistema-atendimento.txt";

    private final AnthropicClient client;
    private final FerramentasAgente ferramentas;
    private final AgenteProperties props;
    private final JsonMapper mapper;
    private final String prompt;

    /**
     * Histórico por telefone. Cresce sem limite e é reenviado inteiro a cada requisição, então o custo
     * sobe a cada turno; aceitável para a demo (uma versão real resumiria ou cortaria o histórico).
     */
    private final Map<String, List<MessageParam>> historico = new ConcurrentHashMap<>();

    /** Um lock por telefone: dois turnos do mesmo cliente não podem sobrescrever o histórico um do outro. */
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    /**
     * Geração do histórico, incrementada a cada reset. Um turno em andamento trabalha numa cópia do
     * histórico anterior ao reset; sem esta checagem ele a gravaria de volta e ressuscitaria a conversa.
     */
    private final AtomicLong geracao = new AtomicLong();

    public AgenteClaude(AnthropicClient client, FerramentasAgente ferramentas, AgenteProperties props,
                        JsonMapper mapper) {
        this.client = client;
        this.ferramentas = ferramentas;
        this.props = props;
        this.mapper = mapper;
        this.prompt = lerPrompt();
    }

    @Override
    public RespostaAgente responder(String telefone, String mensagem) {
        Object lock = locks.computeIfAbsent(telefone, t -> new Object());
        synchronized (lock) {
            return executarTurno(telefone, mensagem);
        }
    }

    @Override
    public void reiniciarConversas() {
        geracao.incrementAndGet();
        historico.clear();
    }

    private RespostaAgente executarTurno(String telefone, String mensagem) {
        long geracaoDoTurno = geracao.get();
        // Trabalha numa cópia: o histórico só é substituído se o turno terminar bem.
        List<MessageParam> turno = new ArrayList<>(historico.getOrDefault(telefone, List.of()));
        turno.add(MessageParam.ofUser(mensagem));
        List<ChamadaFerramenta> chamadas = new ArrayList<>();

        try {
            for (int iteracao = 1; iteracao <= props.maxIteracoes(); iteracao++) {
                Message resposta = client.messages().create(montarRequisicao(turno));
                // A resposta volta inteira (inclusive blocos thinking), nunca só o texto.
                turno.add(resposta.toParam());

                StopReason stopReason = resposta.stopReason().orElse(null);
                if (StopReason.END_TURN.equals(stopReason)) {
                    String texto = juntarTexto(resposta);
                    if (texto.isBlank()) {
                        log.warn("Turno de {} terminou em end_turn sem texto.", telefone);
                        return fallback(chamadas);
                    }
                    // Se houve reset durante o turno, a resposta vai ao cliente mas o histórico antigo não volta.
                    if (geracao.get() == geracaoDoTurno) {
                        historico.put(telefone, turno);
                    }
                    return new RespostaAgente(texto, chamadas);
                }
                if (!StopReason.TOOL_USE.equals(stopReason)) {
                    log.warn("Turno de {} parou com stopReason {}.", telefone, stopReason);
                    return fallback(chamadas);
                }
                if (iteracao == props.maxIteracoes()) {
                    log.warn("Turno de {} atingiu o limite de {} chamadas à API ainda pedindo tools.",
                            telefone, props.maxIteracoes());
                    return fallback(chamadas);
                }
                turno.add(executarTools(telefone, resposta, chamadas));
            }
            // Só alcançável com maxIteracoes < 1.
            log.warn("Turno de {} não executou nenhuma iteração (maxIteracoes = {}).", telefone, props.maxIteracoes());
            return fallback(chamadas);
        } catch (RuntimeException e) {
            log.warn("Falha no turno de {}.", telefone, e);
            return fallback(chamadas);
        }
    }

    private MessageCreateParams montarRequisicao(List<MessageParam> turno) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(props.modelo())
                .maxTokens(MAX_TOKENS)
                .system(prompt)
                .messages(turno)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.of(props.effort())).build());
        ferramentas.definicoes().forEach(builder::addTool);
        return builder.build();
    }

    /** Executa cada tool_use e devolve uma única mensagem de user só com os tool_result. */
    private MessageParam executarTools(String telefone, Message resposta, List<ChamadaFerramenta> chamadas) {
        List<ContentBlockParam> resultados = new ArrayList<>();
        for (ContentBlock bloco : resposta.content()) {
            if (!bloco.isToolUse()) {
                continue;
            }
            ToolUseBlock toolUse = bloco.asToolUse();
            // A entrada é um JsonValue do SDK (Jackson 2): convertida pelo próprio SDK, não pelo JsonMapper.
            Map<String, Object> entrada = toolUse._input().convert(new TypeReference<Map<String, Object>>() {});
            ResultadoFerramenta resultado = ferramentas.executar(telefone, toolUse.name(), entrada);
            chamadas.add(new ChamadaFerramenta(toolUse.name(), mapper.writeValueAsString(entrada),
                    resultado.conteudoJson(), resultado.erro()));
            resultados.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                    .toolUseId(toolUse.id())
                    .content(resultado.conteudoJson())
                    .isError(resultado.erro())
                    .build()));
        }
        return MessageParam.ofUserOfContentBlockParams(resultados);
    }

    private static String juntarTexto(Message resposta) {
        return resposta.content().stream()
                .filter(ContentBlock::isText)
                .map(b -> b.asText().text())
                .collect(Collectors.joining());
    }

    private static RespostaAgente fallback(List<ChamadaFerramenta> chamadas) {
        return new RespostaAgente(MENSAGEM_FALLBACK, chamadas);
    }

    private static String lerPrompt() {
        try {
            return new ClassPathResource(CAMINHO_PROMPT).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler " + CAMINHO_PROMPT, e);
        }
    }
}
