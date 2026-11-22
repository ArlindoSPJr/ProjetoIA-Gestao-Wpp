package app.gestaoIAWpp.agente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;

class LimiteIteracoesAdvisorTest {

    static class ToolDeTeste {
        final AtomicInteger contador = new AtomicInteger();

        @Tool(name = "contar", description = "conta")
        String contar() {
            contador.incrementAndGet();
            return "ok";
        }
    }

    private ChatModel chatModel;
    private ToolDeTeste tool;
    private RegistroTurno registro;
    private ChatClient client;

    @BeforeEach
    void preparar() {
        chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(AnthropicChatOptions.builder().build());
        tool = new ToolDeTeste();
        registro = new RegistroTurno();
        client = ChatClient.builder(chatModel)
                .defaultAdvisors(
                        new LimiteIteracoesAdvisor(ToolCallingManager.builder().maxTotalToolCalls(10).build(), 5),
                        MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().build())
                                .order(ToolCallingAdvisor.DEFAULT_ORDER + 100).build())
                .defaultToolCallbacks(ToolCallbacks.from(tool))
                .build();
    }

    private ChatResponse comTool(String id, String nome, String args) {
        return comTools(List.of(new AssistantMessage.ToolCall(id, "function", nome, args)));
    }

    private ChatResponse comTools(List<AssistantMessage.ToolCall> calls) {
        AssistantMessage msg = AssistantMessage.builder().content("").toolCalls(calls).build();
        return resposta(msg, "tool_use");
    }

    private ChatResponse texto(String t) {
        return resposta(new AssistantMessage(t), "end_turn");
    }

    private ChatResponse resposta(AssistantMessage msg, String finish) {
        return ChatResponse.builder()
                .generations(List.of(new Generation(msg,
                        ChatGenerationMetadata.builder().finishReason(finish).build())))
                .metadata(ChatResponseMetadata.builder().usage(new DefaultUsage(10, 5)).build())
                .build();
    }

    private ChatResponse executar() {
        return client.prompt().user("oi")
                .toolContext(Map.of(RegistroTurno.CHAVE, registro))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "c1").param(RegistroTurno.CHAVE, registro))
                .call().chatResponse();
    }

    @Test
    void paraNaQuintaChamadaSemExecutarAsTools() {
        when(chatModel.call(any(Prompt.class))).thenAnswer(i -> comTool("t", "contar", "{}"));

        ChatResponse r = executar();

        verify(chatModel, times(5)).call(any(Prompt.class));
        assertThat(tool.contador.get()).isEqualTo(4);
        assertThat(r.getResult().getMetadata().getFinishReason()).isEqualTo(LimiteIteracoesAdvisor.FINISH_LIMITE);
        List<IteracaoTurno> it = registro.iteracoes();
        assertThat(it).hasSize(5);
        assertThat(it.get(4).observacao()).contains("limite");
        assertThat(it).allSatisfy(i -> assertThat(i.toolsPedidas()).extracting(ToolPedida::nome).containsExactly("contar"));
    }

    @Test
    void respostaSemToolNaoEhAlterada() {
        when(chatModel.call(any(Prompt.class))).thenReturn(texto("olá"));

        ChatResponse r = executar();

        verify(chatModel, times(1)).call(any(Prompt.class));
        assertThat(r.getResult().getMetadata().getFinishReason()).isEqualTo("end_turn");
        assertThat(registro.iteracoes()).hasSize(1);
        assertThat(registro.iteracoes().get(0).tokensEntrada()).isEqualTo(10);
        assertThat(registro.iteracoes().get(0).tokensSaida()).isEqualTo(5);
    }

    @Test
    void limiteDeToolsDoFrameworkEncerraOTurno() {
        List<AssistantMessage.ToolCall> onze = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            onze.add(new AssistantMessage.ToolCall("t" + i, "function", "contar", "{}"));
        }
        when(chatModel.call(any(Prompt.class))).thenReturn(comTools(onze));

        ChatResponse r = executar();

        verify(chatModel, times(1)).call(any(Prompt.class));
        assertThat(r.getResult().getMetadata().getFinishReason())
                .isEqualTo(ToolCallLimitExceededException.FINISH_REASON);
    }
}
