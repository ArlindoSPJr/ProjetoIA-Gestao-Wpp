package app.gestaoIAWpp.produto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EstoqueServiceTest {

    private EstoqueService estoque;

    @BeforeEach
    void setUp() {
        estoque = new EstoqueService();
    }

    @Test
    void consultarRetornaEstoqueInicial() {
        assertThat(estoque.consultar("P001")).isEqualTo(12);
        assertThat(estoque.consultar("P002")).isZero();
    }

    @Test
    void baixarDiminuiEDevolverAumenta() {
        estoque.baixar("P001", 2);
        assertThat(estoque.consultar("P001")).isEqualTo(10);
        estoque.devolver("P001", 1);
        assertThat(estoque.consultar("P001")).isEqualTo(11);
    }

    @Test
    void baixarAcimaDoDisponivelLancaENaoAltera() {
        var e = assertThrows(EstoqueInsuficienteException.class, () -> estoque.baixar("P005", 4));
        assertThat(e.getDisponivel()).isEqualTo(3);
        assertThat(e.getSolicitado()).isEqualTo(4);
        assertThat(e.getProdutoId()).isEqualTo("P005");
        assertThat(e.getMessage()).isEqualTo("Estoque insuficiente para P005: solicitado 4, disponível 3.");
        assertThat(estoque.consultar("P005")).isEqualTo(3);
    }

    @Test
    void quantidadeMenorQueUmLanca() {
        var e = assertThrows(IllegalArgumentException.class, () -> estoque.baixar("P001", 0));
        assertThat(e.getMessage()).isEqualTo("Quantidade deve ser maior que zero.");
        assertThrows(IllegalArgumentException.class, () -> estoque.devolver("P001", -1));
    }

    @Test
    void idInexistenteLanca() {
        assertThrows(ProdutoNaoEncontradoException.class, () -> estoque.consultar("P999"));
        assertThrows(ProdutoNaoEncontradoException.class, () -> estoque.baixar("P999", 1));
        assertThrows(ProdutoNaoEncontradoException.class, () -> estoque.devolver("P999", 1));
    }

    @Test
    void reiniciarVoltaAoInicial() {
        estoque.baixar("P001", 5);
        estoque.reiniciar();
        assertThat(estoque.consultar("P001")).isEqualTo(12);
    }

    @Test
    void snapshotEUmaCopiaImutavel() {
        var copia = estoque.snapshot();
        assertThat(copia).isEqualTo(CatalogoMock.ESTOQUE_INICIAL);
        assertThrows(UnsupportedOperationException.class, () -> copia.put("P001", 99));
        estoque.baixar("P001", 1);
        assertThat(copia.get("P001")).isEqualTo(12);
    }

    @Test
    void baixasConcorrentesNuncaPassamDoEstoque() throws Exception {
        int threads = 20;
        var pool = Executors.newFixedThreadPool(threads);
        var largada = new CountDownLatch(1);
        var sucessos = new AtomicInteger();
        var falhas = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                largada.await();
                try {
                    estoque.baixar("P005", 1);
                    sucessos.incrementAndGet();
                } catch (EstoqueInsuficienteException e) {
                    falhas.incrementAndGet();
                }
                return null;
            });
        }
        largada.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(sucessos.get()).isEqualTo(3);
        assertThat(falhas.get()).isEqualTo(17);
        assertThat(estoque.consultar("P005")).isZero();
    }
}
