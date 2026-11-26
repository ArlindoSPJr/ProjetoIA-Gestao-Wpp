package app.gestaoIAWpp.produto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

import app.gestaoIAWpp.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(TestcontainersConfiguration.class)
class VarianteRepositoryIT {

	private static final String LOJA_DEMO = "SELECT id FROM empresa WHERE documento = '11222333000181'";

	@Autowired
	VarianteRepository variantes;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PlatformTransactionManager transactionManager;

	private UUID camisaPretaM() {
		return jdbc.queryForObject("""
				SELECT v.id FROM variante v
				JOIN produto p ON p.id = v.produto_id
				WHERE p.nome = 'Camisa Básica' AND v.quantidade_estoque = 12
				""", UUID.class);
	}

	private int saldo(UUID varianteId) {
		return jdbc.queryForObject("SELECT quantidade_estoque FROM variante WHERE id = ?", Integer.class, varianteId);
	}

	private void ajustarSaldo(UUID varianteId, int saldo) {
		jdbc.update("UPDATE variante SET quantidade_estoque = ? WHERE id = ?", saldo, varianteId);
	}

	@Test
	@DisplayName("T03 baixa só acontece com saldo suficiente, inclusive no limite exato")
	void deveBaixarSoComSaldo_quandoQuantidadeNoLimite() {
		UUID id = camisaPretaM();
		ajustarSaldo(id, 2);

		assertThat(variantes.baixarEstoque(id, 2)).isEqualTo(1);
		assertThat(saldo(id)).isZero();

		ajustarSaldo(id, 2);
		assertThat(variantes.baixarEstoque(id, 3)).isZero();
		assertThat(saldo(id)).isEqualTo(2);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	@DisplayName("T04 duas transações disputando a última unidade: só uma vende")
	void deveVenderUltimaUnidadeUmaVez_quandoDuasTransacoesConcorrem() throws Exception {
		UUID id = camisaPretaM();
		int original = saldo(id);
		ajustarSaldo(id, 1);
		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		CountDownLatch largada = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			List<Future<Integer>> resultados = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				resultados.add(pool.submit(() -> {
					largada.await();
					return tx.execute(status -> variantes.baixarEstoque(id, 1));
				}));
			}
			largada.countDown();
			int linhas = 0;
			for (Future<Integer> r : resultados) {
				linhas += r.get(30, TimeUnit.SECONDS);
			}

			assertThat(linhas).isEqualTo(1);
			assertThat(saldo(id)).isZero();
		} finally {
			pool.shutdownNow();
			ajustarSaldo(id, original);
		}
	}

	@Test
	@DisplayName("T09 variante de outra empresa não é encontrada")
	void deveNaoAcharVariante_quandoEmpresaDiferente() {
		UUID id = camisaPretaM();
		UUID lojaDemo = jdbc.queryForObject(LOJA_DEMO, UUID.class);
		UUID outraEmpresa = jdbc.queryForObject("""
				INSERT INTO empresa (razao_social, nome_fantasia, documento)
				VALUES ('Outra LTDA', 'Outra', '99888777000166') RETURNING id
				""", UUID.class);

		assertThat(variantes.daEmpresa(id, lojaDemo)).isPresent();
		assertThat(variantes.daEmpresa(id, outraEmpresa)).isEmpty();
	}

	@Test
	@DisplayName("M08 resumo em lote inclui variante inativa e omite id inexistente, com nome completo carregado")
	void deveIncluirInativaEOmitirInexistente_quandoResumoEmLote() {
		UUID ativa = camisaPretaM();
		UUID inativa = jdbc.queryForObject("""
				SELECT v.id FROM variante v
				JOIN produto p ON p.id = v.produto_id
				WHERE p.nome = 'Camisa Básica' AND v.id <> ? LIMIT 1
				""", UUID.class, ativa);
		jdbc.update("UPDATE variante SET ativo = false WHERE id = ?", inativa);

		List<Variante> resumo = variantes.resumoDasVariantes(List.of(ativa, inativa, UUID.randomUUID()));

		assertThat(resumo).extracting(Variante::getId).containsExactlyInAnyOrder(ativa, inativa);
		assertThat(resumo).allSatisfy(v -> assertThat(v.nomeCompleto()).startsWith("Camisa Básica ("));
	}

}
