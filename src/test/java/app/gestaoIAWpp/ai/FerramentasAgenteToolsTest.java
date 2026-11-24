package app.gestaoIAWpp.ai;

import static app.gestaoIAWpp.ai.FerramentasAgente.CHAVE_TELEFONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.gestaoIAWpp.carrinho.CarrinhoService;
import app.gestaoIAWpp.produto.EstoqueService;
import app.gestaoIAWpp.produto.ProdutoRepositoryEmMemoria;
import app.gestaoIAWpp.produto.ProdutoService;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.ToolExecutionException;
import tools.jackson.databind.json.JsonMapper;

class FerramentasAgenteToolsTest {

    private static final String TEL = "5511999990000";

    private EstoqueService estoque;
    private CarrinhoService carrinho;
    private FerramentasAgente ferramentas;
    private ToolContext ctx;

    @BeforeEach
    void setUp() {
        var produtos = new ProdutoRepositoryEmMemoria();
        estoque = new EstoqueService();
        carrinho = new CarrinhoService(produtos, estoque);
        ferramentas = new FerramentasAgente(new ProdutoService(produtos, estoque), carrinho);
        ctx = new ToolContext(Map.of(CHAVE_TELEFONE, TEL));
    }

    private ToolCallback callback(String nome) {
        return Arrays.stream(ToolCallbacks.from(ferramentas))
                .filter(c -> c.getToolDefinition().name().equals(nome))
                .findFirst().orElseThrow();
    }

    @Test
    void callbacksTemAsCincoToolsComOsNomesDaLoja() {
        ToolCallback[] callbacks = ToolCallbacks.from(ferramentas);
        assertThat(Arrays.stream(callbacks).map(c -> c.getToolDefinition().name())).containsExactlyInAnyOrder(
                "consultar_produto", "consultar_estoque", "adicionar_ao_carrinho", "remover_do_carrinho", "ver_carrinho");
        var mapper = JsonMapper.builder().build();
        var adicionar = mapper.readTree(callback("adicionar_ao_carrinho").getToolDefinition().inputSchema());
        List<String> reqAdicionar = adicionar.get("required").valueStream().map(n -> n.asString()).toList();
        assertThat(reqAdicionar).containsExactlyInAnyOrder("produtoId", "quantidade");
        var remover = mapper.readTree(callback("remover_do_carrinho").getToolDefinition().inputSchema());
        List<String> reqRemover = remover.get("required").valueStream().map(n -> n.asString()).toList();
        assertThat(reqRemover).containsExactly("produtoId");
    }

    @Test
    void adicionarUsaTelefoneDoContextoEBaixaEstoque() {
        ferramentas.adicionarAoCarrinho("P001", 2, ctx);
        assertThat(carrinho.ver(TEL).itens()).hasSize(1);
        assertThat(carrinho.ver(TEL).itens().get(0).quantidade()).isEqualTo(2);
        assertThat(estoque.consultar("P001")).isEqualTo(10);
    }

    @Test
    void removerSemQuantidadeRemoveTudo() {
        ferramentas.adicionarAoCarrinho("P001", 2, ctx);
        ferramentas.removerDoCarrinho("P001", null, ctx);
        assertThat(carrinho.ver(TEL).itens()).isEmpty();
        assertThat(estoque.consultar("P001")).isEqualTo(12);
    }

    @Test
    void quantidadeZeroLancaSemBaixa() {
        assertThatThrownBy(() -> ferramentas.adicionarAoCarrinho("P001", 0, ctx))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(estoque.consultar("P001")).isEqualTo(12);
    }

    @Test
    void estoqueInsuficienteLanca() {
        assertThatThrownBy(() -> ferramentas.adicionarAoCarrinho("P001", 999, ctx))
                .hasMessageContaining("Estoque insuficiente");
    }

    @Test
    void semTelefoneNoContextoFalhaComMensagemClara() {
        var semTelefone = new ToolContext(Map.of("outra", "x"));
        assertThatThrownBy(() -> ferramentas.adicionarAoCarrinho("P001", 1, semTelefone))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Telefone ausente");
        assertThat(estoque.consultar("P001")).isEqualTo(12);
    }

    @Test
    void callbackConverteEntradaESerializaResultado() {
        String json = callback("consultar_produto").call("{\"termo\":\"jaqueta\"}", ctx);
        assertThat(json).contains("\"produtos\"", "\"totalEncontrado\"");
    }

    @Test
    void callbackComFormatoInvalidoLancaToolExecutionException() {
        assertThatThrownBy(() -> callback("adicionar_ao_carrinho")
                .call("{\"produtoId\":\"P001\",\"quantidade\":\"dois\"}", ctx))
                .isInstanceOf(ToolExecutionException.class);
        assertThat(estoque.consultar("P001")).isEqualTo(12);
    }
}
