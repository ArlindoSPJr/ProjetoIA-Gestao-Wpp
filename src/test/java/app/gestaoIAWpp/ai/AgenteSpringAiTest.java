package app.gestaoIAWpp.ai;

import app.gestaoIAWpp.ai.dto.AgenteResponse;
import app.gestaoIAWpp.ai.dto.IteracaoTurnoResponse;
import app.gestaoIAWpp.ai.dto.ToolPedidaResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.gestaoIAWpp.carrinho.CarrinhoService;
import app.gestaoIAWpp.produto.EstoqueService;
import app.gestaoIAWpp.produto.ProdutoRepositoryEmMemoria;
import app.gestaoIAWpp.produto.ProdutoService;
import com.anthropic.models.messages.OutputConfig;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;

class AgenteSpringAiTest {

    private static final String TEL = "5511999990000";

    private ChatModel chatModel;
    private EstoqueService estoque;
    private AgenteSpringAi agente;

    @BeforeEach
    void preparar() {
        chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(AnthropicChatOptions.builder().build());
        var produtos = new ProdutoRepositoryEmMemoria();
        estoque = new EstoqueService();
        var carrinho = new CarrinhoService(produtos, estoque);
        var ferramentas = new FerramentasAgente(new ProdutoService(produtos, estoque), carrinho);
        agente = new AgenteSpringAi(ChatClient.builder(chatModel),
                ToolCallingManager.builder().maxTotalToolCalls(10).build(), ferramentas,
                new AgenteProperties("claude-haiku-5-5", "low", 5));
    }

    // ---------- helpers ----------

    private static ChatResponse comTool(String id, String nome, String args) {
        return comTools(List.of(new AssistantMessage.ToolCall(id, "function", nome, args)));
    }

    private static ChatResponse comTools(List<AssistantMessage.ToolCall> calls) {
        AssistantMessage msg = AssistantMessage.builder().content("").toolCalls(calls).build();
        return resposta(msg, "tool_use");
    }

    private static ChatResponse texto(String t) {
        return resposta(new AssistantMessage(t), "end_turn");
    }

    private static ChatResponse resposta(AssistantMessage msg, String finish) {
        return ChatResponse.builder()
                .generations(List.of(new Generation(msg,
                        ChatGenerationMetadata.builder().finishReason(finish).build())))
                .metadata(ChatResponseMetadata.builder().usage(new DefaultUsage(10, 5)).build())
                .build();
    }

    private List<Prompt> prompts(int chamadas) {
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(chamadas)).call(captor.capture());
        return captor.getAllValues();
    }

    private Prompt ultimoPrompt() {
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, atLeastOnce()).call(captor.capture());
        List<Prompt> todos = captor.getAllValues();
        return todos.get(todos.size() - 1);
    }

    /** As mensagens enviadas ao modelo, sem a SystemMessage. */
    private static List<Message> enviadas(Prompt prompt) {
        return prompt.getInstructions().stream().filter(m -> !(m instanceof SystemMessage)).toList();
    }

    /** Resumo legível das mensagens, para comparar históricos. */
    private static List<String> resumo(Prompt prompt) {
        List<String> r = new ArrayList<>();
        for (Message m : enviadas(prompt)) {
            if (m instanceof UserMessage u) {
                r.add("user:" + u.getText());
            } else if (m instanceof AssistantMessage a) {
                r.add(a.hasToolCalls() ? "assistant:tool_calls" : "assistant:" + a.getText());
            } else if (m instanceof ToolResponseMessage) {
                r.add("tool_result");
            } else {
                r.add(m.getMessageType() + ":" + m.getText());
            }
        }
        return r;
    }

    private static void semToolUse(Prompt prompt) {
        assertThat(enviadas(prompt)).noneMatch(m -> m instanceof ToolResponseMessage);
        assertThat(enviadas(prompt))
                .noneMatch(m -> m instanceof AssistantMessage a && a.hasToolCalls());
    }

    private static ToolResponseMessage toolResult(Prompt prompt) {
        return enviadas(prompt).stream()
                .filter(m -> m instanceof ToolResponseMessage)
                .map(ToolResponseMessage.class::cast)
                .findFirst().orElseThrow();
    }

    // ---------- testes ----------

    @Test
    void toolSeguidaDeTextoRetornaTextoEIteracoes() {
        when(chatModel.call(any(Prompt.class))).thenReturn(
                comTool("t1", "consultar_produto", "{\"termo\":\"camisa preta\"}"), texto("Temos sim!"));

        AgenteResponse r = agente.responder(TEL, "tem camisa preta?");

        assertThat(r.texto()).isEqualTo("Temos sim!");
        assertThat(r.iteracoes()).hasSize(2);
        IteracaoTurnoResponse primeira = r.iteracoes().get(0);
        assertThat(primeira.finishReason()).isEqualTo("tool_use");
        assertThat(primeira.toolsPedidas())
                .containsExactly(new ToolPedidaResponse("consultar_produto", "{\"termo\":\"camisa preta\"}"));
        assertThat(primeira.tokensEntrada()).isEqualTo(10);
        assertThat(primeira.tokensSaida()).isEqualTo(5);
        assertThat(r.iteracoes().get(1).finishReason()).isEqualTo("end_turn");
    }

    @Test
    void segundaChamadaLevaOToolResultDaBusca() {
        when(chatModel.call(any(Prompt.class))).thenReturn(
                comTool("t1", "consultar_produto", "{\"termo\":\"camisa preta\"}"), texto("Temos sim!"));

        agente.responder(TEL, "tem camisa preta?");

        ToolResponseMessage tr = toolResult(prompts(2).get(1));
        assertThat(tr.getResponses()).hasSize(1);
        assertThat(tr.getResponses().get(0).id()).isEqualTo("t1");
        assertThat(tr.getResponses().get(0).responseData()).contains("P001");
    }

    @Test
    void paraNoLimiteDeIteracoesComFallback() {
        when(chatModel.call(any(Prompt.class))).thenAnswer(
                i -> comTool("t", "adicionar_ao_carrinho", "{\"produtoId\":\"P001\",\"quantidade\":1}"));

        AgenteResponse r = agente.responder(TEL, "quero uma camisa");

        assertThat(r.texto()).isEqualTo(AgenteSpringAi.MENSAGEM_FALLBACK);
        verify(chatModel, times(5)).call(any(Prompt.class));
        assertThat(estoque.consultar("P001")).isEqualTo(8);
        assertThat(r.iteracoes()).hasSize(5);
    }

    @Test
    void limiteDeToolsRetornaFallback() {
        List<AssistantMessage.ToolCall> onze = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            onze.add(new AssistantMessage.ToolCall("t" + i, "function", "ver_carrinho", "{}"));
        }
        when(chatModel.call(any(Prompt.class))).thenReturn(comTools(onze));

        AgenteResponse r = agente.responder(TEL, "meu carrinho");

        assertThat(r.texto()).isEqualTo(AgenteSpringAi.MENSAGEM_FALLBACK);
        assertThat(r.iteracoes()).last().extracting(IteracaoTurnoResponse::observacao).asString()
                .contains("limite de tools");
    }

    @Test
    void limiteDeIteracoesNaoDeixaToolUseNaMemoria() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(texto("olá"))
                .thenReturn(comTool("t", "ver_carrinho", "{}"), comTool("t", "ver_carrinho", "{}"),
                        comTool("t", "ver_carrinho", "{}"), comTool("t", "ver_carrinho", "{}"),
                        comTool("t", "ver_carrinho", "{}"))
                .thenReturn(texto("tudo certo"));

        agente.responder(TEL, "oi");
        assertThat(agente.responder(TEL, "meu carrinho").texto()).isEqualTo(AgenteSpringAi.MENSAGEM_FALLBACK);
        agente.responder(TEL, "e agora?");

        Prompt terceiro = prompts(7).get(6);
        semToolUse(terceiro);
        assertThat(resumo(terceiro)).containsExactly("user:oi", "assistant:olá", "user:e agora?");
    }

    @Test
    void limiteDeToolsNaoDeixaToolUseNaMemoria() {
        List<AssistantMessage.ToolCall> onze = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            onze.add(new AssistantMessage.ToolCall("t" + i, "function", "ver_carrinho", "{}"));
        }
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(texto("olá"), comTools(onze), texto("tudo certo"));

        agente.responder(TEL, "oi");
        assertThat(agente.responder(TEL, "meu carrinho").texto()).isEqualTo(AgenteSpringAi.MENSAGEM_FALLBACK);
        agente.responder(TEL, "e agora?");

        Prompt terceiro = prompts(3).get(2);
        semToolUse(terceiro);
        assertThat(resumo(terceiro)).containsExactly("user:oi", "assistant:olá", "user:e agora?");
    }

    @Test
    void erroDaToolVoltaAoModelo() {
        when(chatModel.call(any(Prompt.class))).thenReturn(
                comTool("t1", "adicionar_ao_carrinho", "{\"produtoId\":\"P001\",\"quantidade\":999}"),
                texto("Não temos tudo isso."));

        AgenteResponse r = agente.responder(TEL, "quero 999");

        assertThat(r.texto()).isEqualTo("Não temos tudo isso.");
        assertThat(toolResult(prompts(2).get(1)).getResponses().get(0).responseData())
                .contains("Estoque insuficiente");
    }

    @Test
    void entradaComFormatoInvalidoVoltaAoModeloComoErro() {
        when(chatModel.call(any(Prompt.class))).thenReturn(
                comTool("t1", "adicionar_ao_carrinho", "{\"produtoId\":\"P001\",\"quantidade\":\"dois\"}"),
                texto("Quantas unidades?"));

        AgenteResponse r = agente.responder(TEL, "quero duas");

        assertThat(r.texto()).isEqualTo("Quantas unidades?");
        assertThat(estoque.consultar("P001")).isEqualTo(12);
    }

    @Test
    void turnoComFalhaNaoAlteraMemoria() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(texto("olá"))
                .thenThrow(new RuntimeException("falha simulada"))
                .thenReturn(texto("tudo certo"));

        agente.responder(TEL, "oi");
        AgenteResponse falha = agente.responder(TEL, "tudo bem?");
        agente.responder(TEL, "e agora?");

        assertThat(falha.texto()).isEqualTo(AgenteSpringAi.MENSAGEM_FALLBACK);
        assertThat(resumo(prompts(3).get(2))).containsExactly("user:oi", "assistant:olá", "user:e agora?");
    }

    @Test
    void falhaDepoisDeToolNaoDeixaToolUseNaMemoria() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(comTool("t1", "ver_carrinho", "{}"))
                .thenThrow(new RuntimeException("falha simulada"))
                .thenReturn(texto("tudo certo"));

        assertThat(agente.responder(TEL, "meu carrinho").texto()).isEqualTo(AgenteSpringAi.MENSAGEM_FALLBACK);
        agente.responder(TEL, "e agora?");

        Prompt seguinte = prompts(3).get(2);
        semToolUse(seguinte);
        assertThat(resumo(seguinte)).containsExactly("user:e agora?");
    }

    @Test
    void textoEmBrancoRetornaFallbackSemSujarMemoria() {
        when(chatModel.call(any(Prompt.class))).thenReturn(texto("   "), texto("olá"));

        AgenteResponse r = agente.responder(TEL, "oi");
        agente.responder(TEL, "oi de novo");

        assertThat(r.texto()).isEqualTo(AgenteSpringAi.MENSAGEM_FALLBACK);
        assertThat(r.iteracoes()).last().extracting(IteracaoTurnoResponse::observacao).asString().contains("sem texto");
        assertThat(resumo(prompts(2).get(1))).containsExactly("user:oi de novo");
    }

    @Test
    void memoriaSeparadaPorTelefone() {
        when(chatModel.call(any(Prompt.class))).thenReturn(texto("olá"), texto("oi, B"));

        agente.responder("A", "oi");
        agente.responder("B", "oi, sou B");

        assertThat(resumo(prompts(2).get(1))).containsExactly("user:oi, sou B");
    }

    @Test
    void resetDuranteTurnoNaoRessuscitaConversa() {
        when(chatModel.call(any(Prompt.class)))
                .thenAnswer(i -> {
                    agente.reiniciarConversas();
                    return texto("olá");
                })
                .thenReturn(texto("de novo"));

        assertThat(agente.responder(TEL, "oi").texto()).isEqualTo("olá");
        agente.responder(TEL, "e agora?");

        assertThat(enviadas(ultimoPrompt())).hasSize(1);
    }

    @Test
    void memoriaGuardaToolsEntreTurnos() {
        when(chatModel.call(any(Prompt.class))).thenReturn(
                comTool("t1", "consultar_produto", "{\"termo\":\"camisa preta\"}"), texto("Temos sim!"),
                texto("Custa 49,90."));

        agente.responder(TEL, "tem camisa preta?");
        agente.responder(TEL, "quanto custa?");

        Prompt turno2 = prompts(3).get(2);
        assertThat(toolResult(turno2).getResponses().get(0).id()).isEqualTo("t1");
        assertThat(resumo(turno2)).containsExactly("user:tem camisa preta?", "assistant:tool_calls",
                "tool_result", "assistant:Temos sim!", "user:quanto custa?");
    }

    @Test
    void janelaCheiaComecaEmMensagemDoCliente() {
        List<ChatResponse> respostas = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            respostas.add(comTool("t" + i, "ver_carrinho", "{}"));
            respostas.add(texto("resposta " + i));
        }
        respostas.add(texto("fim"));
        when(chatModel.call(any(Prompt.class))).thenReturn(respostas.get(0),
                respostas.subList(1, respostas.size()).toArray(ChatResponse[]::new));

        for (int i = 0; i < 6; i++) {
            agente.responder(TEL, "mensagem " + i);
        }

        List<Message> sexto = enviadas(ultimoPrompt());
        assertThat(sexto.get(0)).isInstanceOf(UserMessage.class);
        assertThat(sexto.size()).isLessThanOrEqualTo(21);
    }

    @Test
    void requisicaoLevaModeloEffortEAsCincoTools() {
        when(chatModel.call(any(Prompt.class))).thenReturn(texto("olá"));

        agente.responder(TEL, "oi");

        Prompt primeiro = prompts(1).get(0);
        assertThat(primeiro.getOptions()).isInstanceOf(AnthropicChatOptions.class);
        AnthropicChatOptions opcoes = (AnthropicChatOptions) primeiro.getOptions();
        assertThat(opcoes.getModel()).isEqualTo("claude-haiku-5-5");
        assertThat(opcoes.getMaxTokens()).isEqualTo(16000);
        assertThat(opcoes.getOutputConfig().effort()).hasValue(OutputConfig.Effort.LOW);
        assertThat(((ToolCallingChatOptions) opcoes).getToolCallbacks()).hasSize(5);
        assertThat(primeiro.getInstructions()).filteredOn(m -> m instanceof SystemMessage)
                .singleElement().extracting(Message::getText).asString().isNotBlank();
    }

    @Test
    void maxTokensRetornaFallbackSemSujarMemoria() {
        when(chatModel.call(any(Prompt.class))).thenReturn(
                texto("olá"), resposta(new AssistantMessage("resposta cortada"), "max_tokens"), texto("tudo certo"));

        agente.responder(TEL, "oi");
        AgenteResponse r = agente.responder(TEL, "conta uma história");
        agente.responder(TEL, "e agora?");

        assertThat(r.texto()).isEqualTo(AgenteSpringAi.MENSAGEM_FALLBACK);
        assertThat(r.iteracoes()).last().extracting(IteracaoTurnoResponse::observacao).asString().contains("max_tokens");
        assertThat(resumo(prompts(3).get(2))).containsExactly("user:oi", "assistant:olá", "user:e agora?");
    }

    @Test
    void limiteDeToolsSomandoRodadasRetornaFallback() {
        when(chatModel.call(any(Prompt.class))).thenReturn(
                comTools(verCarrinho(0, 4)), comTools(verCarrinho(4, 4)), comTools(verCarrinho(8, 3)));

        AgenteResponse r = agente.responder(TEL, "meu carrinho");

        assertThat(r.texto()).isEqualTo(AgenteSpringAi.MENSAGEM_FALLBACK);
        assertThat(r.iteracoes()).last().extracting(IteracaoTurnoResponse::observacao).asString()
                .contains("limite de tools");
        verify(chatModel, times(3)).call(any(Prompt.class));

        when(chatModel.call(any(Prompt.class))).thenReturn(texto("ok"));
        agente.responder(TEL, "e agora?");
        Prompt seguinte = ultimoPrompt();
        semToolUse(seguinte);
        assertThat(resumo(seguinte)).containsExactly("user:e agora?");
    }

    private static List<AssistantMessage.ToolCall> verCarrinho(int inicio, int quantas) {
        List<AssistantMessage.ToolCall> calls = new ArrayList<>();
        for (int i = inicio; i < inicio + quantas; i++) {
            calls.add(new AssistantMessage.ToolCall("t" + i, "function", "ver_carrinho", "{}"));
        }
        return calls;
    }
}
