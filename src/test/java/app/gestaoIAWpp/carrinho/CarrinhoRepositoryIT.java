package app.gestaoIAWpp.carrinho;

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

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(TestcontainersConfiguration.class)
class CarrinhoRepositoryIT {

	@Autowired
	CarrinhoRepository carrinhos;

	@Autowired
	JdbcTemplate jdbc;

	private UUID lojaDemo() {
		return jdbc.queryForObject("SELECT id FROM empresa WHERE documento = '11222333000181'", UUID.class);
	}

	private UUID novoCliente(UUID empresa) {
		return jdbc.queryForObject("INSERT INTO cliente (empresa_id, user_id) VALUES (?, ?) RETURNING id",
				UUID.class, empresa, "u-" + UUID.randomUUID());
	}

	private UUID camisaPretaM() {
		return jdbc.queryForObject("""
				SELECT v.id FROM variante v
				JOIN produto p ON p.id = v.produto_id
				WHERE p.nome = 'Camisa Básica' AND v.quantidade_estoque = 12
				""", UUID.class);
	}

	@Test
	@DisplayName("T05 carrinho aberto criado só uma vez")
	void deveCriarUmCarrinhoAberto_quandoChamadoDuasVezes() {
		UUID empresa = lojaDemo();
		UUID cliente = novoCliente(empresa);

		assertThat(carrinhos.criarAbertoSeNaoExiste(empresa, cliente)).isEqualTo(1);
		assertThat(carrinhos.criarAbertoSeNaoExiste(empresa, cliente)).isZero();

		assertThat(jdbc.queryForObject("SELECT count(*) FROM carrinho WHERE cliente_id = ? AND status = 'ABERTO'",
				Integer.class, cliente)).isEqualTo(1);
		assertThat(carrinhos.findByClienteIdAndStatus(cliente, StatusCarrinho.ABERTO)).isPresent();
	}

	@Test
	@DisplayName("T06 adicionar de novo a mesma variante soma a quantidade num único item")
	void deveSomarQuantidade_quandoMesmaVarianteDeNovo() {
		UUID empresa = lojaDemo();
		UUID cliente = novoCliente(empresa);
		UUID variante = camisaPretaM();
		carrinhos.criarAbertoSeNaoExiste(empresa, cliente);
		UUID carrinho = carrinhos.findByClienteIdAndStatus(cliente, StatusCarrinho.ABERTO).orElseThrow().getId();

		carrinhos.somarItem(carrinho, variante, 2);
		carrinhos.somarItem(carrinho, variante, 1);

		assertThat(jdbc.queryForList("SELECT quantidade FROM item_carrinho WHERE carrinho_id = ?", Integer.class,
				carrinho)).containsExactly(3);
	}

}
