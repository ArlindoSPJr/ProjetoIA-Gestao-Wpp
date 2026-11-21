package app.gestaoIAWpp.agente;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.ObjectMappers;
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
        int inicioNovas = turno.size();
        turno.add(MessageParam.ofUser(mensagem));
        List<ChamadaFerramenta> chamadas = new ArrayList<>();
        List<IteracaoAgente> iteracoes = new ArrayList<>();
        int max = props.maxIteracoes();

        try {
            for (int iteracao = 1; iteracao <= max; iteracao++) {
                MessageCreateParams params = montarRequisicao(turno);
                List<MessageParam> novas = turno.subList(inicioNovas, turno.size());
                EnvioApi envio = resumirEnvio(params, novas);
                log.info("[{}] iteração {}/{}: enviando {} mensagens ({} novas) ao {}",
                        telefone, iteracao, max, envio.totalMensagens(), novas.size(), envio.modelo());
                log.debug("[{}] iteração {} - requisição:\n{}", telefone, iteracao, envio.requisicaoJson());

                Message resposta;
                try {
                    resposta = client.messages().create(params);
                } catch (RuntimeException e) {
                    log.warn("Falha no turno de {} (iteração {}).", telefone, iteracao, e);
                    iteracoes.add(new IteracaoAgente(iteracao, envio, null, List.of(),
                            "Falha na chamada à API: " + e.getMessage()));
                    return fallback(chamadas, iteracoes);
                }
                RetornoApi retorno = resumirRetorno(resposta);
                log.info("[{}] iteração {}/{}: stop_reason={}, tokens entrada={} saída={}, tools pedidas={}",
                        telefone, iteracao, max, retorno.stopReason(), retorno.tokensEntrada(),
                        retorno.tokensSaida(), nomesDasTools(resposta));
                log.debug("[{}] iteração {} - resposta:\n{}", telefone, iteracao, retorno.conteudoJson());
                // A resposta volta inteira (inclusive blocos thinking), nunca só o texto.
                turno.add(resposta.toParam());

                StopReason stopReason = resposta.stopReason().orElse(null);
                if (StopReason.END_TURN.equals(stopReason)) {
                    String texto = juntarTexto(resposta);
                    if (texto.isBlank()) {
                        log.warn("Turno de {} terminou em end_turn sem texto.", telefone);
                        iteracoes.add(new IteracaoAgente(iteracao, envio, retorno, List.of(),
                                "O modelo terminou (end_turn) sem texto."));
                        return fallback(chamadas, iteracoes);
                    }
                    iteracoes.add(new IteracaoAgente(iteracao, envio, retorno, List.of(), null));
                    // Se houve reset durante o turno, a resposta vai ao cliente mas o histórico antigo não volta.
                    if (geracao.get() == geracaoDoTurno) {
                        historico.put(telefone, turno);
                    }
                    return new RespostaAgente(texto, chamadas, iteracoes);
                }
                if (!StopReason.TOOL_USE.equals(stopReason)) {
                    log.warn("Turno de {} parou com stopReason {}.", telefone, stopReason);
                    iteracoes.add(new IteracaoAgente(iteracao, envio, retorno, List.of(),
                            "Parou com stop_reason " + retorno.stopReason() + "."));
                    return fallback(chamadas, iteracoes);
                }
                if (iteracao == max) {
                    log.warn("Turno de {} atingiu o limite de {} chamadas à API ainda pedindo tools.", telefone, max);
                    iteracoes.add(new IteracaoAgente(iteracao, envio, retorno, List.of(),
                            "Atingiu o limite de " + max + " chamadas à API; as tools pedidas não foram executadas."));
                    return fallback(chamadas, iteracoes);
                }
                List<ChamadaFerramenta> daIteracao = new ArrayList<>();
                // A próxima requisição reenvia esta resposta (já mostrada aqui); as "novas" serão só os tool_result.
                inicioNovas = turno.size();
                turno.add(executarTools(telefone, resposta, daIteracao));
                chamadas.addAll(daIteracao);
                iteracoes.add(new IteracaoAgente(iteracao, envio, retorno, daIteracao, null));
            }
            // Só alcançável com maxIteracoes < 1.
            log.warn("Turno de {} não executou nenhuma iteração (maxIteracoes = {}).", telefone, max);
            return fallback(chamadas, iteracoes);
        } catch (RuntimeException e) {
            log.warn("Falha no turno de {}.", telefone, e);
            return fallback(chamadas, iteracoes);
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
            String entradaJson = mapper.writeValueAsString(entrada);
            log.info("[{}] tool {} entrada={} -> {}", telefone, toolUse.name(), entradaJson,
                    resultado.erro() ? "erro" : "ok");
            log.debug("[{}] tool {} resultado: {}", telefone, toolUse.name(), resultado.conteudoJson());
            chamadas.add(new ChamadaFerramenta(toolUse.name(), entradaJson, resultado.conteudoJson(), resultado.erro()));
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

    private EnvioApi resumirEnvio(MessageCreateParams params, List<MessageParam> novas) {
        return new EnvioApi(props.modelo(), props.effort(), params.maxTokens(), params.messages().size(),
                params.tools().map(List::size).orElse(0), jsonDoSdk(novas), jsonDoSdk(params._body()));
    }

    private static RetornoApi resumirRetorno(Message resposta) {
        String stopReason = resposta.stopReason().map(StopReason::asString).orElse(null);
        return new RetornoApi(stopReason, resposta.usage().inputTokens(), resposta.usage().outputTokens(),
                jsonDoSdk(resposta.content()));
    }

    private static List<String> nomesDasTools(Message resposta) {
        return resposta.content().stream().filter(ContentBlock::isToolUse).map(b -> b.asToolUse().name()).toList();
    }

    /**
     * Serializa objetos do SDK com o mapper do próprio SDK (Jackson 2), que conhece os tipos dele.
     * É só para exibição: se falhar, o turno segue normalmente.
     */
    private static String jsonDoSdk(Object valor) {
        try {
            return ObjectMappers.jsonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(valor);
        } catch (Exception e) {
            log.debug("Não foi possível serializar {} para exibição.", valor.getClass().getSimpleName(), e);
            return "\"(não foi possível serializar: " + e.getClass().getSimpleName() + ")\"";
        }
    }

    private static RespostaAgente fallback(List<ChamadaFerramenta> chamadas, List<IteracaoAgente> iteracoes) {
        return new RespostaAgente(MENSAGEM_FALLBACK, chamadas, iteracoes);
    }

    private static String lerPrompt() {
        try {
            return new ClassPathResource(CAMINHO_PROMPT).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler " + CAMINHO_PROMPT, e);
        }
    }
}
