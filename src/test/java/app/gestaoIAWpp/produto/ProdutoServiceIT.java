package app.gestaoIAWpp.produto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

import app.gestaoIAWpp.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Sem transação do teste: a baixa e a devolução precisam abrir a própria transação (os UPDATEs são {@code @Modifying}). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import({TestcontainersConfiguration.class, ProdutoService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProdutoServiceIT {

	@Autowired
	ProdutoService produtos;

	@Autowired
	JdbcTemplate jdbc;

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
	@DisplayName("M04 baixarEstoque funciona fora de transação do chamador")
	void deveBaixarEstoque_quandoChamadoForaDeTransacao() {
		UUID id = camisaPretaM();
		int original = saldo(id);
		try {
			ajustarSaldo(id, 2);

			produtos.baixarEstoque(id, 1);

			assertThat(saldo(id)).isEqualTo(1);
		} finally {
			ajustarSaldo(id, original);
		}
	}

	@Test
	@DisplayName("M07 devolverEstoque funciona fora de transação do chamador")
	void deveDevolverEstoque_quandoChamadoForaDeTransacao() {
		UUID id = camisaPretaM();
		int original = saldo(id);
		try {
			ajustarSaldo(id, 1);

			produtos.devolverEstoque(id, 2);

			assertThat(saldo(id)).isEqualTo(3);
		} finally {
			ajustarSaldo(id, original);
		}
	}
}
