package app.gestaoIAWpp.agente;

import static org.assertj.core.api.Assertions.assertThat;

import app.gestaoIAWpp.carrinho.CarrinhoService;
import app.gestaoIAWpp.produto.EstoqueService;
import app.gestaoIAWpp.produto.ProdutoRepositoryEmMemoria;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class FerramentasAgenteTest {

    private static final String TEL = "5511999990000";

    private EstoqueService estoque;
    private CarrinhoService carrinho;
    private FerramentasAgente ferramentas;

    @BeforeEach
    void setUp() {
        var produtos = new ProdutoRepositoryEmMemoria();
        estoque = new EstoqueService();
        carrinho = new CarrinhoService(produtos, estoque);
        ferramentas = new FerramentasAgente(produtos, estoque, carrinho, JsonMapper.builder().build());
    }

    @Test
    void definicoesTemAsCincoTools() {
        assertThat(ferramentas.definicoes()).extracting(t -> t.name()).containsExactly(
                "consultar_produto", "consultar_estoque", "adicionar_ao_carrinho", "remover_do_carrinho", "ver_carrinho");
    }

    @Test
    void consultarProdutoRetornaIdPrecoEEstoque() {
        var r = ferramentas.executar(TEL, "consultar_produto", Map.of("termo", "camisa preta"));
        assertThat(r.erro()).isFalse();
        assertThat(r.conteudoJson()).contains("\"id\":\"P001\"", "\"quantidadeDisponivel\":12", "\"emEstoque\":false");
    }

    @Test
    void consultarProdutoComMaisDeCincoLimitaEPedeDetalhes() {
        var r = ferramentas.executar(TEL, "consultar_produto", Map.of("termo", "camisa calça tênis boné"));
        assertThat(r.erro()).isFalse();
        assertThat(r.conteudoJson()).contains("\"totalEncontrado\":8", "Foram encontrados 8 produtos", "Peça ao cliente mais detalhes");
        assertThat(r.conteudoJson().split("\"id\":", -1)).hasSize(6);
    }

    @Test
    void empateDePontuacaoPoeComEstoqueAntesDeEsgotado() {
        var r = ferramentas.executar(TEL, "consultar_produto", Map.of("termo", "camisa basica"));
        assertThat(r.conteudoJson()).containsSubsequence("\"id\":\"P001\"", "\"id\":\"P003\"", "\"id\":\"P002\"", "\"id\":\"P004\"");
    }

    @Test
    void consultarProdutoComAteCincoNaoTemMensagem() {
        var r = ferramentas.executar(TEL, "consultar_produto", Map.of("termo", "camisa"));
        assertThat(r.conteudoJson()).contains("\"totalEncontrado\":4").doesNotContain("mensagem");
    }

    @Test
    void consultarProdutoSemResultadoNaoEhErro() {
        var r = ferramentas.executar(TEL, "consultar_produto", Map.of("termo", "geladeira"));
        assertThat(r.erro()).isFalse();
        assertThat(r.conteudoJson()).contains("Nenhum produto encontrado");
    }

    @Test
    void adicionarUsaTelefoneDoContextoEBaixaEstoque() {
        ferramentas.executar(TEL, "adicionar_ao_carrinho", Map.of("produto_id", "P001", "quantidade", 2));
        assertThat(carrinho.ver(TEL).itens()).hasSize(1);
        assertThat(estoque.consultar("P001")).isEqualTo(10);
    }

    @Test
    void quantidadeInvalidaViraErroSemBaixa() {
        for (Object q : List.of(0, -1, "dois", 1.5, 2.0)) {
            var r = ferramentas.executar(TEL, "adicionar_ao_carrinho", Map.of("produto_id", "P001", "quantidade", q));
            assertThat(r.erro()).isTrue();
            assertThat(r.conteudoJson()).contains("quantidade deve ser um número inteiro maior que zero");
        }
        assertThat(estoque.consultar("P001")).isEqualTo(12);
    }

    @Test
    void estoqueInsuficienteViraErro() {
        var r = ferramentas.executar(TEL, "adicionar_ao_carrinho", Map.of("produto_id", "P001", "quantidade", 999));
        assertThat(r.erro()).isTrue();
        assertThat(r.conteudoJson()).contains("disponível 12");
    }

    @Test
    void produtoInexistenteViraErro() {
        assertThat(ferramentas.executar(TEL, "consultar_estoque", Map.of("produto_id", "P999")).conteudoJson())
                .contains("Produto P999 não encontrado.");
    }

    @Test
    void parametroAusenteViraErro() {
        assertThat(ferramentas.executar(TEL, "adicionar_ao_carrinho", Map.of("quantidade", 1)).conteudoJson())
                .contains("Parâmetro obrigatório ausente: produto_id.");
    }

    @Test
    void ferramentaDesconhecidaViraErro() {
        assertThat(ferramentas.executar(TEL, "apagar_tudo", Map.of()).erro()).isTrue();
    }

    @Test
    void removerSemQuantidadeRemoveTudo() {
        ferramentas.executar(TEL, "adicionar_ao_carrinho", Map.of("produto_id", "P001", "quantidade", 2));
        ferramentas.executar(TEL, "remover_do_carrinho", Map.of("produto_id", "P001"));
        assertThat(carrinho.ver(TEL).itens()).isEmpty();
    }
}
