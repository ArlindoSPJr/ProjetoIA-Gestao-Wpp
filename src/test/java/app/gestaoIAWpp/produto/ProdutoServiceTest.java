package app.gestaoIAWpp.produto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ProdutoServiceTest {

    private ProdutoService service;

    @BeforeEach
    void setUp() {
        service = new ProdutoService(new ProdutoRepositoryEmMemoria(), new EstoqueService());
    }

    @Test
    void buscarRetornaIdPrecoEEstoque() {
        var r = service.buscar("camisa preta");
        var p = r.produtos().get(0);
        assertThat(p.id()).isEqualTo("P001");
        assertThat(p.quantidadeDisponivel()).isEqualTo(12);
        assertThat(p.emEstoque()).isTrue();
        assertThat(p.preco()).isNotNull();
    }

    @Test
    void buscarComMaisDeCincoLimitaEPedeDetalhes() {
        var r = service.buscar("camisa calça tênis boné");
        assertThat(r.totalEncontrado()).isEqualTo(8);
        assertThat(r.produtos()).hasSize(ProdutoService.LIMITE_RESULTADOS_BUSCA);
        assertThat(r.mensagem()).contains("Foram encontrados 8 produtos", "Peça ao cliente mais detalhes");
    }

    @Test
    void empateDePontuacaoPoeComEstoqueAntesDeEsgotado() {
        var r = service.buscar("camisa basica");
        assertThat(r.produtos()).extracting(ProdutoResumo::id)
                .containsSubsequence("P001", "P003", "P002", "P004");
    }

    @Test
    void buscarComAteCincoNaoTemMensagem() {
        assertThat(service.buscar("camisa preta").mensagem()).isNull();
    }

    @Test
    void buscarSemResultadoTemMensagemENaoLanca() {
        var r = service.buscar("xyzinexistente");
        assertThat(r.produtos()).isEmpty();
        assertThat(r.totalEncontrado()).isZero();
        assertThat(r.mensagem()).isEqualTo("Nenhum produto encontrado para 'xyzinexistente'.");
    }

    @Test
    void consultarDisponibilidadeRetornaQuantidade() {
        var d = service.consultarDisponibilidade("P001");
        assertThat(d.produtoId()).isEqualTo("P001");
        assertThat(d.quantidadeDisponivel()).isEqualTo(12);
        assertThat(d.emEstoque()).isTrue();
    }

    @Test
    void consultarDisponibilidadeDeProdutoInexistenteLanca() {
        assertThatThrownBy(() -> service.consultarDisponibilidade("P999"))
                .isInstanceOf(ProdutoNaoEncontradoException.class)
                .hasMessage("Produto P999 não encontrado.");
    }

    @Test
    void resultadoSemMensagemNaoSerializaOCampo() throws Exception {
        String json = JsonMapper.builder().build().writeValueAsString(service.buscar("camisa preta"));
        assertThat(json).doesNotContain("\"mensagem\"");
    }
}
