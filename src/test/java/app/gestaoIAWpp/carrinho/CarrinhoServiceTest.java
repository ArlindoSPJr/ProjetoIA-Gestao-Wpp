package app.gestaoIAWpp.carrinho;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import app.gestaoIAWpp.produto.CatalogoTeste;
import app.gestaoIAWpp.produto.VarianteNaoEncontradaException;
import app.gestaoIAWpp.produto.VarianteRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regras de decisão do carrinho com os repositórios mockados. O que depende do SQL (upsert, índice
 * parcial, rollback, estoque real) está no {@code CarrinhoServiceIT}.
 */
class CarrinhoServiceTest {

    private static final UUID EMPRESA = UUID.randomUUID();
    private static final UUID CLIENTE = UUID.randomUUID();

    private VarianteRepository variantes;
    private CarrinhoRepository carrinhos;
    private CarrinhoService carrinho;
    private UUID pretaM;
    private UUID brancaM;

    @BeforeEach
    void setUp() {
        var catalogo = CatalogoTeste.lojaDemo(EMPRESA);
        variantes = catalogo.varianteRepository();
        carrinhos = CarrinhoTeste.repositorio(variantes);
        carrinho = new CarrinhoService(variantes, carrinhos);
        pretaM = catalogo.variante("Camisa Básica (Preta / M)").getId();
        brancaM = catalogo.variante("Camisa Básica (Branca / M)").getId();
    }

    @Test
    @DisplayName("T17 item com 3, remover 1: item fica com 2 e devolve 1")
    void deveDiminuirItemEDevolver_quandoRemoveMenosQueOItem() {
        carrinho.adicionar(EMPRESA, CLIENTE, pretaM, 3);

        CarrinhoResumo r = carrinho.remover(EMPRESA, CLIENTE, pretaM, 1);

        assertThat(r.itens()).singleElement().satisfies(i -> assertThat(i.quantidade()).isEqualTo(2));
        assertThat(r.total()).isEqualTo(new BigDecimal("99.80"));
        verify(variantes).devolverEstoque(pretaM, 1);
    }

    @Test
    @DisplayName("T17 item com 3, remover 3: item sai e devolve 3")
    void deveTirarItemEDevolverTudo_quandoRemoveAQuantidadeDoItem() {
        carrinho.adicionar(EMPRESA, CLIENTE, pretaM, 3);

        assertThat(carrinho.remover(EMPRESA, CLIENTE, pretaM, 3).itens()).isEmpty();
        verify(variantes).devolverEstoque(pretaM, 3);
    }

    @Test
    @DisplayName("T17 item com 3, remover sem quantidade: item sai e devolve 3")
    void deveTirarItemEDevolverTudo_quandoRemoveSemQuantidade() {
        carrinho.adicionar(EMPRESA, CLIENTE, pretaM, 3);
        carrinho.adicionar(EMPRESA, CLIENTE, brancaM, 1);

        CarrinhoResumo r = carrinho.remover(EMPRESA, CLIENTE, pretaM, null);

        assertThat(r.itens()).extracting(ItemCarrinhoResumo::varianteId).containsExactly(brancaM);
        verify(variantes).devolverEstoque(pretaM, 3);
    }

    @Test
    @DisplayName("T18 adicionar com 0 é recusado antes de tocar os repositórios")
    void deveRecusarAdicionar_quandoQuantidadeZero() {
        assertThatThrownBy(() -> carrinho.adicionar(EMPRESA, CLIENTE, pretaM, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Quantidade deve ser maior que zero.");
        verifyNoInteractions(variantes, carrinhos);
    }

    @Test
    @DisplayName("T18 remover com 0 é recusado antes de tocar os repositórios")
    void deveRecusarRemover_quandoQuantidadeZero() {
        assertThatThrownBy(() -> carrinho.remover(EMPRESA, CLIENTE, pretaM, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Quantidade deve ser maior que zero.");
        verifyNoInteractions(variantes, carrinhos);
    }

    @Test
    @DisplayName("T18 adicionar com 1 é aceito e o resumo traz o preço atual da variante")
    void deveAceitarAdicionar_quandoQuantidadeUm() {
        CarrinhoResumo r = carrinho.adicionar(EMPRESA, CLIENTE, pretaM, 1);

        assertThat(r.itens()).containsExactly(
                new ItemCarrinhoResumo(pretaM, "Camisa Básica (Preta / M)", new BigDecimal("49.90"), 1));
        assertThat(r.total()).isEqualTo(new BigDecimal("49.90"));
        verify(variantes).baixarEstoque(pretaM, 1);
    }

    @Test
    @DisplayName("T18 remover com 1 é aceito")
    void deveAceitarRemover_quandoQuantidadeUm() {
        carrinho.adicionar(EMPRESA, CLIENTE, pretaM, 1);

        assertThat(carrinho.remover(EMPRESA, CLIENTE, pretaM, 1).itens()).isEmpty();
        verify(variantes).devolverEstoque(pretaM, 1);
    }

    @Test
    @DisplayName("T19 remover variante fora do carrinho: erro e nenhum estoque devolvido")
    void deveRecusarRemover_quandoVarianteNaoEstaNoCarrinho() {
        carrinho.adicionar(EMPRESA, CLIENTE, pretaM, 1);

        assertThatThrownBy(() -> carrinho.remover(EMPRESA, CLIENTE, brancaM, 1))
                .isInstanceOf(ItemNaoEstaNoCarrinhoException.class)
                .hasMessage("Variante " + brancaM + " não está no carrinho.");
        verify(variantes, never()).devolverEstoque(any(), anyInt());
    }

    @Test
    @DisplayName("Review Focus: cliente sem carrinho aberto vê lista vazia e total 0.00")
    void deveDevolverCarrinhoVazio_quandoClienteNaoTemCarrinhoAberto() {
        CarrinhoResumo r = carrinho.ver(CLIENTE);

        assertThat(r.itens()).isEmpty();
        assertThat(r.total()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("Review Focus: remover sem carrinho aberto é 'não está no carrinho', não NullPointerException")
    void deveRecusarRemover_quandoClienteNaoTemCarrinhoAberto() {
        assertThatThrownBy(() -> carrinho.remover(EMPRESA, CLIENTE, pretaM, null))
                .isInstanceOf(ItemNaoEstaNoCarrinhoException.class)
                .hasMessage("Variante " + pretaM + " não está no carrinho.");
        verify(variantes, never()).devolverEstoque(any(), anyInt());
    }

    @Test
    @DisplayName("T19 remover só enxerga o carrinho aberto da empresa informada")
    void deveRecusarRemover_quandoCarrinhoEDeOutraEmpresa() {
        carrinho.adicionar(EMPRESA, CLIENTE, pretaM, 1);

        assertThatThrownBy(() -> carrinho.remover(UUID.randomUUID(), CLIENTE, pretaM, null))
                .isInstanceOf(ItemNaoEstaNoCarrinhoException.class);
        verify(variantes, never()).devolverEstoque(any(), anyInt());
    }

    @Test
    @DisplayName("T20 adicionar variante não encontrada na empresa: erro sem baixar estoque")
    void deveRecusarAdicionar_quandoVarianteNaoEncontrada() {
        UUID inexistente = UUID.randomUUID();

        assertThatThrownBy(() -> carrinho.adicionar(EMPRESA, CLIENTE, inexistente, 1))
                .isInstanceOf(VarianteNaoEncontradaException.class)
                .hasMessage("Variante " + inexistente + " não encontrada.");
        verify(variantes, never()).baixarEstoque(any(), anyInt());
        verifyNoInteractions(carrinhos);
    }
}
