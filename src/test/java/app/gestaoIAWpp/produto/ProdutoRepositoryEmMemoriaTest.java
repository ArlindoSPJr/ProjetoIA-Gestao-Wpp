package app.gestaoIAWpp.produto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProdutoRepositoryEmMemoriaTest {

    private final ProdutoRepositoryEmMemoria repo = new ProdutoRepositoryEmMemoria();

    private static List<String> ids(List<ProdutoEncontrado> r) {
        return r.stream().map(e -> e.produto().id()).toList();
    }

    @Test
    void buscaPorTermoRetornaProdutosQueContemAsPalavras() {
        assertThat(ids(repo.buscar("camisa preta"))).containsExactly("P001", "P002", "P003", "P004");
        assertThat(repo.buscar("camisa preta")).extracting(ProdutoEncontrado::pontuacao).containsExactly(4, 4, 2, 2);
    }

    @Test
    void palavraNoNomeValeMaisQueNaDescricao() {
        assertThat(repo.buscar("jeans escura")).extracting(e -> e.produto().id(), ProdutoEncontrado::pontuacao)
                .containsExactly(tuple("P005", 3), tuple("P006", 2));
    }

    @Test
    void buscaIgnoraAcentoEMaiuscula() {
        assertThat(ids(repo.buscar("TENIS"))).containsExactly("P007");
        assertThat(ids(repo.buscar("tênis"))).containsExactly("P007");
        assertThat(ids(repo.buscar("calca"))).containsExactly("P005", "P006");
    }

    @Test
    void buscaOrdenaPorPontuacao() {
        assertThat(repo.buscar("preta camisa").get(0).produto().id()).isEqualTo("P001");
        assertThat(repo.buscar("camisa")).hasSize(4);
    }

    @Test
    void buscaRetornaTodosOsEncontradosSemLimite() {
        assertThat(repo.buscar("a b c camisa calça tênis boné")).hasSize(8);
    }

    @Test
    void buscaIgnoraStopwords() {
        assertThat(ids(repo.buscar("camisa de algodao"))).containsExactly("P001", "P002", "P003", "P004");
    }

    @Test
    void buscaMantemNumeros() {
        assertThat(ids(repo.buscar("tenis 40"))).containsExactly("P007");
    }

    @Test
    void buscaSemCorrespondenciaRetornaVazio() {
        assertThat(repo.buscar("geladeira")).isEmpty();
    }

    @Test
    void buscaComTermoVazioOuNuloRetornaVazio() {
        assertThat(repo.buscar("  ")).isEmpty();
        assertThat(repo.buscar(null)).isEmpty();
    }

    @Test
    void buscarPorIdEncontraEIgnoraInexistente() {
        assertThat(repo.buscarPorId("P003")).map(Produto::nome).contains("Camisa Básica Branca M");
        assertThat(repo.buscarPorId("P999")).isEmpty();
    }

    @Test
    void listarTodosRetornaOsOito() {
        assertThat(repo.listarTodos()).hasSize(8);
    }

    @Test
    void buscaIgnoraPontuacao() {
        assertThat(ids(repo.buscar("camisa preta?"))).containsExactly("P001", "P002", "P003", "P004");
        assertThat(ids(repo.buscar("tênis, branco!"))).containsExactly("P007");
    }
}
