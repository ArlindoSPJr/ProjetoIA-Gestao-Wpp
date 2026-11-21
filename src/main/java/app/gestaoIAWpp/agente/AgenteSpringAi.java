package app.gestaoIAWpp.agente;

import com.anthropic.models.messages.OutputConfig;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * Agente de atendimento com o Spring AI: o loop de tool calling é do framework.
 *
 * <p>A cadeia de advisors, em ordem: {@link LimiteIteracoesAdvisor} (o loop, com teto de chamadas à API
 * e registro de cada iteração), {@link MessageChatMemoryAdvisor} (histórico por telefone, salvo a cada
 * iteração) e {@link SimpleLoggerAdvisor} (requisição e resposta em DEBUG).
 *
 * <p>Como a memória é gravada durante o turno, um turno que termina mal deixaria nela mensagens
 * incompletas (ex.: um tool_use sem o tool_result). Por isso o turno tira um snapshot antes e, em
 * qualquer desfecho de falha, restaura a memória para ele.
 */
@Service
public class AgenteSpringAi implements AgenteAtendimento {

    static final String MENSAGEM_FALLBACK =
            "Desculpe, não consegui concluir agora. Vou chamar um atendente para te ajudar.";

    private static final Logger log = LoggerFactory.getLogger(AgenteSpringAi.class);
    private static final int MAX_TOKENS = 16000;
    private static final String CAMINHO_PROMPT = "prompts/sistema-atendimento.txt";

    private final ChatClient chatClient;
    private final InMemoryChatMemoryRepository repositorio = new InMemoryChatMemoryRepository();
    private final ChatMemory memoria = MessageWindowChatMemory.builder().chatMemoryRepository(repositorio).build();

    /** Um lock por telefone: dois turnos do mesmo cliente não podem intercalar escritas na memória. */
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    /**
     * Geração da memória, incrementada a cada reset. Um turno em andamento durante um reset não pode
     * gravar de volta (nem restaurar) o histórico anterior, senão ressuscitaria a conversa.
     */
    private final AtomicLong geracao = new AtomicLong();

    public AgenteSpringAi(ChatClient.Builder builder, ToolCallingManager manager, FerramentasAgente ferramentas,
                          AgenteProperties props) {
        this.chatClient = builder
                .defaultSystem(lerPrompt())
                .defaultOptions(AnthropicChatOptions.builder()
                        .model(props.modelo())
                        .maxTokens(MAX_TOKENS)
                        .effort(OutputConfig.Effort.of(props.effort())))
                .defaultToolCallbacks(ToolCallbacks.from(ferramentas))
                .defaultAdvisors(
                        new LimiteIteracoesAdvisor(manager, props.maxIteracoes()),
                        MessageChatMemoryAdvisor.builder(memoria).order(ToolCallingAdvisor.DEFAULT_ORDER + 100).build(),
                        new SimpleLoggerAdvisor(ToolCallingAdvisor.DEFAULT_ORDER + 200))
                .build();
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
        for (String id : repositorio.findConversationIds()) {
            repositorio.deleteByConversationId(id);
        }
    }

    private RespostaAgente executarTurno(String telefone, String mensagem) {
        long geracaoDoTurno = geracao.get();
        List<Message> snapshot = List.copyOf(memoria.get(telefone));
        RegistroTurno registro = new RegistroTurno();

        ChatResponse resposta;
        try {
            resposta = chatClient.prompt()
                    .user(mensagem)
                    .toolContext(Map.of(FerramentasAgente.CHAVE_TELEFONE, telefone, RegistroTurno.CHAVE, registro))
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, telefone).param(RegistroTurno.CHAVE, registro))
                    .call()
                    .chatResponse();
        } catch (RuntimeException e) {
            log.warn("Falha no turno de {}.", telefone, e);
            registro.observar("Falha: " + e.getMessage());
            return fallback(telefone, snapshot, geracaoDoTurno, registro);
        }

        String finish = resposta == null || resposta.getResult() == null
                ? null : resposta.getResult().getMetadata().getFinishReason();
        if (LimiteIteracoesAdvisor.FINISH_LIMITE.equals(finish)) {
            // A observação já foi registrada pelo advisor.
            return fallback(telefone, snapshot, geracaoDoTurno, registro);
        }
        if (ToolCallLimitExceededException.FINISH_REASON.equals(finish)) {
            registro.observar("Atingiu o limite de tools por turno; o turno foi interrompido.");
            return fallback(telefone, snapshot, geracaoDoTurno, registro);
        }
        String texto = resposta == null || resposta.getResult() == null
                ? null : resposta.getResult().getOutput().getText();
        if (texto == null || texto.isBlank()) {
            registro.observar("O modelo terminou sem texto.");
            return fallback(telefone, snapshot, geracaoDoTurno, registro);
        }

        // Houve reset durante o turno: a resposta vai ao cliente, mas a conversa não volta à memória.
        if (geracao.get() != geracaoDoTurno) {
            memoria.clear(telefone);
        }
        return new RespostaAgente(texto, registro.iteracoes());
    }

    private RespostaAgente fallback(String telefone, List<Message> snapshot, long geracaoDoTurno,
                                    RegistroTurno registro) {
        restaurar(telefone, snapshot, geracaoDoTurno);
        return new RespostaAgente(MENSAGEM_FALLBACK, registro.iteracoes());
    }

    /** Volta a memória ao estado de antes do turno (ou só a limpa, se houve reset no meio). */
    private void restaurar(String telefone, List<Message> snapshot, long geracaoDoTurno) {
        memoria.clear(telefone);
        if (geracao.get() == geracaoDoTurno && !snapshot.isEmpty()) {
            memoria.add(telefone, snapshot);
        }
    }

    private static String lerPrompt() {
        try {
            return new ClassPathResource(CAMINHO_PROMPT).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler " + CAMINHO_PROMPT, e);
        }
    }
}
