package app.gestaoIAWpp.carrinho;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.gestaoIAWpp.produto.EstoqueInsuficienteException;
import app.gestaoIAWpp.produto.EstoqueService;
import app.gestaoIAWpp.produto.ProdutoNaoEncontradoException;
import app.gestaoIAWpp.produto.ProdutoRepositoryEmMemoria;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CarrinhoServiceTest {

    private static final String TEL = "5511999990000";

    private EstoqueService estoque;
    private CarrinhoService carrinho;
    private final JsonMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        estoque = new EstoqueService();
        carrinho = new CarrinhoService(new ProdutoRepositoryEmMemoria(), estoque);
    }

    @Test
    void adicionarBaixaEstoqueERetornaResumo() {
        var r = carrinho.adicionar(TEL, "P001", 2);
        assertThat(r.itens()).containsExactly(new ItemCarrinho("P001", "Camisa Básica Preta M", new BigDecimal("49.90"), 2));
        assertThat(r.total()).isEqualByComparingTo("99.80");
        assertThat(estoque.consultar("P001")).isEqualTo(10);
    }

    @Test
    void adicionarMesmoProdutoSomaQuantidade() {
        carrinho.adicionar(TEL, "P001", 2);
        assertThat(carrinho.adicionar(TEL, "P001", 3).itens().get(0).quantidade()).isEqualTo(5);
    }

    @Test
    void segundaAdicaoAcimaDoEstoqueFalhaSemAlterarNada() {
        carrinho.adicionar(TEL, "P001", 10);
        assertThrows(EstoqueInsuficienteException.class, () -> carrinho.adicionar(TEL, "P001", 5));
        assertThat(carrinho.ver(TEL).itens().get(0).quantidade()).isEqualTo(10);
        assertThat(estoque.consultar("P001")).isEqualTo(2);
    }

    @Test
    void adicionarProdutoInexistenteLanca() {
        assertThrows(ProdutoNaoEncontradoException.class, () -> carrinho.adicionar(TEL, "P999", 1));
    }

    @Test
    void removerParcialDevolveEstoque() {
        carrinho.adicionar(TEL, "P001", 3);
        carrinho.remover(TEL, "P001", 1);
        assertThat(carrinho.ver(TEL).itens().get(0).quantidade()).isEqualTo(2);
        assertThat(estoque.consultar("P001")).isEqualTo(10);
    }

    @Test
    void removerSemQuantidadeRemoveTudo() {
        carrinho.adicionar(TEL, "P001", 3);
        assertThat(carrinho.remover(TEL, "P001", null).itens()).isEmpty();
        assertThat(estoque.consultar("P001")).isEqualTo(12);
    }

    @Test
    void removerMaisQueOCarrinhoRemoveTudo() {
        carrinho.adicionar(TEL, "P001", 2);
        assertThat(carrinho.remover(TEL, "P001", 50).itens()).isEmpty();
        assertThat(estoque.consultar("P001")).isEqualTo(12);
    }

    @Test
    void removerItemAusenteLanca() {
        assertThrows(ItemNaoEstaNoCarrinhoException.class, () -> carrinho.remover(TEL, "P003", 1));
    }

    @Test
    void removerQuantidadeInvalidaLancaAntesDeChecarPresenca() {
        var e = assertThrows(IllegalArgumentException.class, () -> carrinho.remover(TEL, "P003", 0));
        assertThat(e.getMessage()).isEqualTo("Quantidade deve ser maior que zero.");
    }

    @Test
    void carrinhosSaoIsoladosPorTelefone() {
        carrinho.adicionar("A", "P001", 1);
        assertThat(carrinho.ver("B").itens()).isEmpty();
        assertThat(carrinho.ver("B").total()).isEqualByComparingTo("0.00");
    }

    @Test
    void limparTodosEsvaziaCarrinhosSemDevolverEstoque() {
        carrinho.adicionar(TEL, "P001", 2);
        carrinho.limparTodos();
        assertThat(carrinho.ver(TEL).itens()).isEmpty();
        assertThat(estoque.consultar("P001")).isEqualTo(10);
    }

    @Test
    void reiniciarTudoLimpaCarrinhosERestauraEstoque() {
        carrinho.adicionar(TEL, "P001", 2);
        carrinho.reiniciarTudo();
        assertThat(carrinho.ver(TEL).itens()).isEmpty();
        assertThat(estoque.consultar("P001")).isEqualTo(12);
    }

    @Test
    void itemSerializaSubtotal() {
        assertThat(mapper.writeValueAsString(carrinho.adicionar(TEL, "P001", 2)))
                .contains("\"subtotal\":99.80", "\"total\":99.80");
    }
}
