package app.gestaoIAWpp.ai;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallingManager;

/**
 * Loop de tool calling do Spring AI com um teto próprio de chamadas à API por turno, e o registro
 * de cada iteração no {@link RegistroTurno} (lido do contexto pela chave {@link RegistroTurno#CHAVE}).
 *
 * <p>A cadeia exige um {@code MessageChatMemoryAdvisor} downstream (com {@code order} maior que o deste
 * advisor): como o histórico não é gerido aqui ({@code conversationHistoryEnabled = false}), é ele quem
 * guarda as mensagens a cada iteração.
 */
public class LimiteIteracoesAdvisor extends ToolCallingAdvisor {

    public static final String FINISH_LIMITE = "limiteIteracoes";

    private final int maxIteracoes;

    public LimiteIteracoesAdvisor(ToolCallingManager manager, int maxIteracoes) {
        super(manager, DEFAULT_TOOL_EXECUTION_ELIGIBILITY_CHECKER, ToolCallingAdvisor.DEFAULT_ORDER, false);
        this.maxIteracoes = maxIteracoes;
    }

    @Override
    protected ChatClientRequest doBeforeCall(ChatClientRequest request, CallAdvisorChain chain) {
        RegistroTurno registro = RegistroTurno.de(request.context());
        if (registro == null) {
            throw new IllegalStateException("RegistroTurno ausente do contexto (chave " + RegistroTurno.CHAVE + ")");
        }
        registro.iniciarIteracao();
        return super.doBeforeCall(request, chain);
    }

    @Override
    protected ChatClientResponse doAfterCall(ChatClientResponse response, CallAdvisorChain chain) {
        ChatClientResponse base = super.doAfterCall(response, chain);
        RegistroTurno registro = RegistroTurno.de(base.context());
        ChatResponse chatResponse = base.chatResponse();
        if (registro == null || chatResponse == null) {
            return base;
        }

        List<ToolPedidaResponse> pedidas = new ArrayList<>();
        for (Generation g : chatResponse.getResults()) {
            for (AssistantMessage.ToolCall tc : g.getOutput().getToolCalls()) {
                pedidas.add(new ToolPedidaResponse(tc.name(), tc.arguments()));
            }
        }
        String finish = chatResponse.getResult() == null
                ? null : chatResponse.getResult().getMetadata().getFinishReason();
        Usage usage = chatResponse.getMetadata().getUsage();
        long entrada = usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
        long saida = usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
        registro.concluirIteracao(finish, entrada, saida, pedidas);

        if (chatResponse.hasToolCalls() && registro.iteracoesIniciadas() >= maxIteracoes) {
            registro.observar("Atingiu o limite de " + maxIteracoes
                    + " chamadas à API; as tools pedidas não foram executadas.");
            Generation limite = new Generation(new AssistantMessage(""),
                    ChatGenerationMetadata.builder().finishReason(FINISH_LIMITE).build());
            return base.mutate()
                    .chatResponse(ChatResponse.builder().from(chatResponse).generations(List.of(limite)).build())
                    .build();
        }
        return base;
    }
}
