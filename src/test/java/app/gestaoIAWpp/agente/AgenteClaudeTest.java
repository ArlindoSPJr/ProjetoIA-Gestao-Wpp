package app.gestaoIAWpp.agente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.gestaoIAWpp.carrinho.CarrinhoService;
import app.gestaoIAWpp.produto.EstoqueService;
import app.gestaoIAWpp.produto.ProdutoRepositoryEmMemoria;
import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.DirectCaller;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.ThinkingBlock;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.Usage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class AgenteClaudeTest {

    private static final String TEL = "5511999990000";

    private AnthropicClient client;
    private EstoqueService estoque;
    private AgenteClaude agente;

    @BeforeEach
    void setUp() {
        client = mock(AnthropicClient.class, RETURNS_DEEP_STUBS);
        var produtos = new ProdutoRepositoryEmMemoria();
        estoque = new EstoqueService();
        var carrinho = new CarrinhoService(produtos, estoque);
        var mapper = JsonMapper.builder().build();
        var ferramentas = new FerramentasAgente(produtos, estoque, carrinho, mapper);
        agente = new AgenteClaude(client, ferramentas, new AgenteProperties("claude-haiku-5-5", "low", 5), mapper);
    }

    @Test
    void toolUseDepoisTextoExecutaFerramentaERetornaTexto() {
        when(client.messages().create(any(MessageCreateParams.class)))
                .thenReturn(respostaToolUse("toolu_1", "consultar_produto", Map.of("termo", "camisa preta")),
                        respostaTexto("Temos sim!"));

        var r = agente.responder(TEL, "tem camisa preta?");

        assertThat(r.texto()).isEqualTo("Temos sim!");
        assertThat(r.chamadas()).singleElement().satisfies(c -> {
            assertThat(c.nome()).isEqualTo("consultar_produto");
            assertThat(c.entradaJson()).contains("camisa preta");
            assertThat(c.resultadoJson()).contains("P001");
            assertThat(c.erro()).isFalse();
        });
    }

    @Test
    void segundaRequisicaoLevaToolResultComOMesmoId() {
        when(client.messages().create(any(MessageCreateParams.class)))
                .thenReturn(respostaToolUse("toolu_1", "consultar_produto", Map.of("termo", "camisa preta")),
                        respostaTexto("Temos sim!"));

        agente.responder(TEL, "tem camisa preta?");

        List<MessageCreateParams> requisicoes = capturarRequisicoes(2);
        List<MessageParam> mensagens = requisicoes.get(1).messages();
        assertThat(mensagens).hasSize(3);
        MessageParam ultima = mensagens.get(2);
        assertThat(ultima.role()).isEqualTo(MessageParam.Role.USER);
        var blocos = ultima.content().asContentBlockParams();
        assertThat(blocos).singleElement().satisfies(b -> {
            assertThat(b.isToolResult()).isTrue();
            assertThat(b.asToolResult().toolUseId()).isEqualTo("toolu_1");
        });
    }

    @Test
    void paraNoLimiteDeIteracoesComFallback() {
        when(client.messages().create(any(MessageCreateParams.class)))
                .thenReturn(respostaToolUse("t", "ver_carrinho", Map.of()));

        assertThat(agente.responder(TEL, "oi").texto()).isEqualTo(AgenteClaude.MENSAGEM_FALLBACK);
        verify(client.messages(), times(5)).create(any(MessageCreateParams.class));
    }

    @Test
    void turnoComFalhaNaoAlteraHistorico() {
        when(client.messages().create(any(MessageCreateParams.class)))
                .thenReturn(respostaTexto("olá"))
                .thenThrow(new RuntimeException("falha simulada"))
                .thenReturn(respostaTexto("tudo certo"));

        agente.responder(TEL, "oi");
        assertThat(agente.responder(TEL, "quero uma camisa").texto()).isEqualTo(AgenteClaude.MENSAGEM_FALLBACK);
        assertThat(agente.responder(TEL, "e agora?").texto()).isEqualTo("tudo certo");

        List<MessageParam> mensagens = capturarRequisicoes(3).get(2).messages();
        assertThat(mensagens).hasSize(3);
        assertThat(mensagens.get(0).content().asString()).isEqualTo("oi");
        assertThat(mensagens.get(1).role()).isEqualTo(MessageParam.Role.ASSISTANT);
        assertThat(mensagens.get(2).content().asString()).isEqualTo("e agora?");
    }

    @Test
    void naUltimaIteracaoNaoExecutaTools() {
        when(client.messages().create(any(MessageCreateParams.class)))
                .thenReturn(respostaToolUse("t", "adicionar_ao_carrinho", Map.of("produto_id", "P001", "quantidade", 1)));

        var r = agente.responder(TEL, "quero a camisa preta M");

        verify(client.messages(), times(5)).create(any(MessageCreateParams.class));
        assertThat(estoque.consultar("P001")).isEqualTo(8);
        assertThat(r.chamadas()).hasSize(4);
        assertThat(r.texto()).isEqualTo(AgenteClaude.MENSAGEM_FALLBACK);
    }

    @Test
    void endTurnSemTextoRetornaFallback() {
        when(client.messages().create(any(MessageCreateParams.class)))
                .thenReturn(resposta(StopReason.END_TURN, ContentBlock.ofThinking(
                                ThinkingBlock.builder().thinking("").signature("sig").build())),
                        respostaTexto("olá"));

        assertThat(agente.responder(TEL, "oi").texto()).isEqualTo(AgenteClaude.MENSAGEM_FALLBACK);

        agente.responder(TEL, "oi de novo");
        assertThat(capturarRequisicoes(2).get(1).messages()).hasSize(1);
    }

    @Test
    void refusalRetornaFallback() {
        when(client.messages().create(any(MessageCreateParams.class)))
                .thenReturn(resposta(StopReason.REFUSAL, ContentBlock.ofText(texto("não posso"))));

        assertThat(agente.responder(TEL, "oi").texto()).isEqualTo(AgenteClaude.MENSAGEM_FALLBACK);
    }

    @Test
    void historicoSeparadoPorTelefone() {
        when(client.messages().create(any(MessageCreateParams.class)))
                .thenReturn(respostaTexto("olá A"), respostaTexto("olá B"));

        agente.responder("A", "oi");
        agente.responder("B", "oi");

        assertThat(capturarRequisicoes(2).get(1).messages()).hasSize(1);
    }

    @Test
    void requisicaoLevaModeloPromptETools() {
        when(client.messages().create(any(MessageCreateParams.class))).thenReturn(respostaTexto("olá"));

        agente.responder(TEL, "oi");

        MessageCreateParams params = capturarRequisicoes(1).get(0);
        assertThat(params.model().asString()).isEqualTo("claude-haiku-5-5");
        assertThat(params.maxTokens()).isEqualTo(16000L);
        assertThat(params.system()).isPresent();
        assertThat(params.system().get().asString()).isNotBlank();
        assertThat(params.tools()).isPresent();
        assertThat(params.tools().get()).hasSize(5);
    }

    @Test
    void historicoLimpoAposEstourarLimite() {
        when(client.messages().create(any(MessageCreateParams.class)))
                .thenReturn(respostaToolUse("t", "ver_carrinho", Map.of()),
                        respostaToolUse("t", "ver_carrinho", Map.of()),
                        respostaToolUse("t", "ver_carrinho", Map.of()),
                        respostaToolUse("t", "ver_carrinho", Map.of()),
                        respostaToolUse("t", "ver_carrinho", Map.of()),
                        respostaTexto("olá"));

        assertThat(agente.responder(TEL, "oi").texto()).isEqualTo(AgenteClaude.MENSAGEM_FALLBACK);
        assertThat(agente.responder(TEL, "oi de novo").texto()).isEqualTo("olá");

        assertThat(capturarRequisicoes(6).get(5).messages()).hasSize(1);
    }

    @Test
    void resetDuranteTurnoNaoRessuscitaConversa() {
        when(client.messages().create(any(MessageCreateParams.class)))
                .thenAnswer(inv -> {
                    agente.reiniciarConversas();
                    return respostaTexto("olá");
                })
                .thenReturn(respostaTexto("de novo"));

        assertThat(agente.responder(TEL, "oi").texto()).isEqualTo("olá");
        agente.responder(TEL, "oi outra vez");

        assertThat(capturarRequisicoes(2).get(1).messages()).hasSize(1);
    }

    @Test
    void iteracoesRegistramEnvioRetornoETools() {
        when(client.messages().create(any(MessageCreateParams.class)))
                .thenReturn(respostaToolUse("toolu_1", "consultar_produto", Map.of("termo", "camisa preta")),
                        respostaTexto("Temos sim!"));

        var r = agente.responder(TEL, "tem camisa preta?");

        assertThat(r.iteracoes()).hasSize(2);
        IteracaoAgente primeira = r.iteracoes().get(0);
        assertThat(primeira.numero()).isEqualTo(1);
        assertThat(primeira.envio().modelo()).isEqualTo("claude-haiku-5-5");
        assertThat(primeira.envio().effort()).isEqualTo("low");
        assertThat(primeira.envio().maxTokens()).isEqualTo(16000L);
        assertThat(primeira.envio().totalMensagens()).isEqualTo(1);
        assertThat(primeira.envio().totalTools()).isEqualTo(5);
        assertThat(primeira.envio().novasMensagensJson()).contains("tem camisa preta?");
        assertThat(primeira.envio().requisicaoJson()).contains("claude-haiku-5-5", "consultar_produto", "tem camisa preta?");
        assertThat(primeira.retorno().stopReason()).isEqualTo("tool_use");
        assertThat(primeira.retorno().tokensEntrada()).isEqualTo(10);
        assertThat(primeira.retorno().tokensSaida()).isEqualTo(5);
        // A página lê "type", "name", "input" e "text" desse JSON para mostrar os blocos.
        assertThat(primeira.retorno().conteudoJson())
                .contains("\"type\" : \"tool_use\"", "\"name\" : \"consultar_produto\"", "toolu_1", "camisa preta");
        assertThat(primeira.ferramentas()).singleElement()
                .satisfies(c -> assertThat(c.nome()).isEqualTo("consultar_produto"));
        assertThat(primeira.observacao()).isNull();

        IteracaoAgente segunda = r.iteracoes().get(1);
        assertThat(segunda.numero()).isEqualTo(2);
        assertThat(segunda.envio().totalMensagens()).isEqualTo(3);
        assertThat(segunda.envio().novasMensagensJson()).contains("toolu_1", "P001").doesNotContain("tem camisa preta?");
        assertThat(segunda.retorno().stopReason()).isEqualTo("end_turn");
        assertThat(segunda.retorno().conteudoJson()).contains("\"type\" : \"text\"", "Temos sim!");
        assertThat(segunda.ferramentas()).isEmpty();
        assertThat(r.chamadas()).hasSize(1);
    }

    @Test
    void primeiraIteracaoDoSegundoTurnoMostraSoAMensagemNova() {
        when(client.messages().create(any(MessageCreateParams.class)))
                .thenReturn(respostaTexto("olá"), respostaTexto("tudo bem"));

        agente.responder(TEL, "oi");
        var r = agente.responder(TEL, "como vai?");

        IteracaoAgente it = r.iteracoes().get(0);
        assertThat(it.envio().totalMensagens()).isEqualTo(3);
        assertThat(it.envio().novasMensagensJson()).contains("como vai?").doesNotContain("\"oi\"");
    }

    @Test
    void falhaNaApiRegistraIteracaoSemRetorno() {
        when(client.messages().create(any(MessageCreateParams.class)))
                .thenThrow(new RuntimeException("falha simulada"));

        var r = agente.responder(TEL, "oi");

        assertThat(r.texto()).isEqualTo(AgenteClaude.MENSAGEM_FALLBACK);
        assertThat(r.iteracoes()).singleElement().satisfies(it -> {
            assertThat(it.envio().totalMensagens()).isEqualTo(1);
            assertThat(it.retorno()).isNull();
            assertThat(it.observacao()).contains("falha simulada");
        });
    }

    @Test
    void limiteDeIteracoesFicaRegistradoNaUltima() {
        when(client.messages().create(any(MessageCreateParams.class)))
                .thenReturn(respostaToolUse("t", "ver_carrinho", Map.of()));

        var r = agente.responder(TEL, "oi");

        assertThat(r.iteracoes()).hasSize(5);
        assertThat(r.iteracoes().subList(0, 4)).allSatisfy(it -> assertThat(it.ferramentas()).hasSize(1));
        IteracaoAgente ultima = r.iteracoes().get(4);
        assertThat(ultima.ferramentas()).isEmpty();
        assertThat(ultima.observacao()).contains("limite");
    }

    @Test
    void stopReasonInesperadoFicaRegistrado() {
        when(client.messages().create(any(MessageCreateParams.class)))
                .thenReturn(resposta(StopReason.REFUSAL, ContentBlock.ofText(texto("não posso"))));

        var r = agente.responder(TEL, "oi");

        assertThat(r.iteracoes()).singleElement().satisfies(it -> {
            assertThat(it.retorno().stopReason()).isEqualTo("refusal");
            assertThat(it.observacao()).contains("refusal");
        });
    }

    private List<MessageCreateParams> capturarRequisicoes(int quantidade) {
        var captor = ArgumentCaptor.forClass(MessageCreateParams.class);
        verify(client.messages(), times(quantidade)).create(captor.capture());
        return captor.getAllValues();
    }

    private static Message respostaTexto(String texto) {
        return resposta(StopReason.END_TURN, ContentBlock.ofText(texto(texto)));
    }

    private static Message respostaToolUse(String id, String nome, Map<String, Object> input) {
        var toolUse = ToolUseBlock.builder()
                .id(id)
                .name(nome)
                .input(JsonValue.from(input))
                .caller(DirectCaller.builder().build())
                .build();
        return resposta(StopReason.TOOL_USE, ContentBlock.ofToolUse(toolUse));
    }

    private static TextBlock texto(String texto) {
        return TextBlock.builder().text(texto).citations(List.of()).build();
    }

    private static Message resposta(StopReason stopReason, ContentBlock... blocos) {
        return Message.builder()
                .id("msg_teste")
                .model("claude-haiku-5-5")
                .content(List.of(blocos))
                .stopReason(stopReason)
                .stopSequence(Optional.empty())
                .stopDetails(Optional.empty())
                .container(Optional.empty())
                .diagnostics(Optional.empty())
                .usage(Usage.builder()
                        .inputTokens(10)
                        .outputTokens(5)
                        .cacheCreation(Optional.empty())
                        .cacheCreationInputTokens(Optional.empty())
                        .cacheReadInputTokens(Optional.empty())
                        .inferenceGeo(Optional.empty())
                        .outputTokensDetails(Optional.empty())
                        .serverToolUse(Optional.empty())
                        .serviceTier(Optional.empty())
                        .speed(Optional.empty())
                        .build())
                .build();
    }
}
