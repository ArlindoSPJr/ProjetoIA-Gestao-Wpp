package app.gestaoIAWpp.carrinho;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import app.gestaoIAWpp.produto.CatalogoTeste;
import app.gestaoIAWpp.produto.EstoqueInsuficienteException;
import app.gestaoIAWpp.produto.VarianteNaoEncontradaException;
import app.gestaoIAWpp.produto.VarianteRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Adaptado à assinatura final; a Task 5 reescreve estes testes com o carrinho no banco. */
class CarrinhoServiceTest {

    private static final UUID EMPRESA = UUID.randomUUID();
    private static final UUID CLIENTE = UUID.randomUUID();

    private VarianteRepository variantes;
    private CarrinhoService carrinho;
    private UUID pretaM;
    private UUID brancaM;
    private final JsonMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        var catalogo = CatalogoTeste.lojaDemo(EMPRESA);
        variantes = catalogo.varianteRepository();
        carrinho = new CarrinhoService(variantes);
        pretaM = catalogo.variante("Camisa Básica (Preta / M)").getId();
        brancaM = catalogo.variante("Camisa Básica (Branca / M)").getId();
    }

    @Test
    void adicionarBaixaEstoqueERetornaResumo() {
        var r = carrinho.adicionar(EMPRESA, CLIENTE, pretaM, 2);
        assertThat(r.itens()).containsExactly(
                new ItemCarrinhoResumo(pretaM, "Camisa Básica (Preta / M)", new BigDecimal("49.90"), 2));
        assertThat(r.total()).isEqualByComparingTo("99.80");
        verify(variantes).baixarEstoque(pretaM, 2);
    }

    @Test
    void adicionarMesmaVarianteSomaQuantidade() {
        carrinho.adicionar(EMPRESA, CLIENTE, pretaM, 2);
        assertThat(carrinho.adicionar(EMPRESA, CLIENTE, pretaM, 3).itens().get(0).quantidade()).isEqualTo(5);
    }

    @Test
    void adicaoAcimaDoEstoqueFalhaSemAlterarOCarrinho() {
        carrinho.adicionar(EMPRESA, CLIENTE, pretaM, 2);
        var e = assertThrows(EstoqueInsuficienteException.class, () -> carrinho.adicionar(EMPRESA, CLIENTE, pretaM, 13));
        assertThat(e.getMessage()).isEqualTo("Estoque insuficiente para Camisa Básica (Preta / M): solicitado 13, disponível 12.");
        assertThat(carrinho.ver(CLIENTE).itens().get(0).quantidade()).isEqualTo(2);
    }

    @Test
    void adicionarVarianteInexistenteLanca() {
        assertThrows(VarianteNaoEncontradaException.class, () -> carrinho.adicionar(EMPRESA, CLIENTE, UUID.randomUUID(), 1));
    }

    @Test
    void removerParcialDevolveEstoque() {
        carrinho.adicionar(EMPRESA, CLIENTE, pretaM, 3);
        carrinho.remover(EMPRESA, CLIENTE, pretaM, 1);
        assertThat(carrinho.ver(CLIENTE).itens().get(0).quantidade()).isEqualTo(2);
        verify(variantes).devolverEstoque(pretaM, 1);
    }

    @Test
    void removerMaisQueOCarrinhoRemoveTudo() {
        carrinho.adicionar(EMPRESA, CLIENTE, pretaM, 2);
        assertThat(carrinho.remover(EMPRESA, CLIENTE, pretaM, 50).itens()).isEmpty();
        verify(variantes).devolverEstoque(pretaM, 2);
    }

    @Test
    void removerItemAusenteLanca() {
        var e = assertThrows(ItemNaoEstaNoCarrinhoException.class, () -> carrinho.remover(EMPRESA, CLIENTE, brancaM, 1));
        assertThat(e.getMessage()).isEqualTo("Variante " + brancaM + " não está no carrinho.");
    }

    @Test
    void removerQuantidadeInvalidaLancaAntesDeChecarPresenca() {
        var e = assertThrows(IllegalArgumentException.class, () -> carrinho.remover(EMPRESA, CLIENTE, brancaM, 0));
        assertThat(e.getMessage()).isEqualTo("Quantidade deve ser maior que zero.");
    }

    @Test
    void carrinhosSaoIsoladosPorCliente() {
        carrinho.adicionar(EMPRESA, CLIENTE, pretaM, 1);
        UUID outro = UUID.randomUUID();
        assertThat(carrinho.ver(outro).itens()).isEmpty();
        assertThat(carrinho.ver(outro).total()).isEqualByComparingTo("0.00");
    }

    @Test
    void reiniciarDemoEsvaziaCarrinhosEDevolveEstoque() {
        carrinho.adicionar(EMPRESA, CLIENTE, pretaM, 2);
        carrinho.reiniciarDemo(EMPRESA);
        assertThat(carrinho.ver(CLIENTE).itens()).isEmpty();
        verify(variantes).devolverEstoque(pretaM, 2);
    }

    @Test
    void itemSerializaSubtotal() {
        assertThat(mapper.writeValueAsString(carrinho.adicionar(EMPRESA, CLIENTE, pretaM, 2)))
                .contains("\"subtotal\":99.80", "\"total\":99.80");
    }
}
