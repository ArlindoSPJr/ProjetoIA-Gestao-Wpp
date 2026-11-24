package app.gestaoIAWpp.produto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

import app.gestaoIAWpp.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = NONE)
@Import(TestcontainersConfiguration.class)
class ProdutoRepositoryIT {

	@Autowired
	ProdutoRepository produtos;

	@Autowired
	VarianteRepository variantes;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	EntityManager em;

	private UUID lojaDemo() {
		return jdbc.queryForObject("SELECT id FROM empresa WHERE documento = '11222333000181'", UUID.class);
	}

	@Test
	@DisplayName("T10 produto inativo fica fora do catálogo; variante inativa vem na coleção mas não é achada")
	void deveIgnorarInativos_quandoCarregaCatalogo() {
		jdbc.update("UPDATE produto SET ativo = false WHERE nome = 'Calça Jeans Feminina'");
		UUID pretaG = jdbc.queryForObject("""
				SELECT v.id FROM variante v JOIN produto p ON p.id = v.produto_id
				WHERE p.nome = 'Camisa Básica' AND v.quantidade_estoque = 0
				""", UUID.class);
		jdbc.update("UPDATE variante SET ativo = false WHERE id = ?", pretaG);

		List<Produto> catalogo = produtos.catalogoAtivo(lojaDemo());

		assertThat(catalogo).extracting(Produto::getNome)
				.hasSize(5)
				.doesNotContain("Calça Jeans Feminina");
		Produto basica = catalogo.stream().filter(p -> p.getNome().equals("Camisa Básica")).findFirst().orElseThrow();
		assertThat(basica.getVariantes()).extracting(Variante::getId).contains(pretaG);
		assertThat(variantes.daEmpresa(pretaG, lojaDemo())).isEmpty();
	}

	@Test
	@DisplayName("Review Focus: catálogo com variantes, valores e opções em no máximo 4 statements")
	void deveCarregarCatalogoSemNMais1_quandoPercorreColecoes() {
		UUID empresa = lojaDemo();
		em.clear();
		Statistics stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
		stats.clear();

		List<Produto> catalogo = produtos.catalogoAtivo(empresa);
		int percorridos = 0;
		for (Produto p : catalogo) {
			for (Opcao o : p.getOpcoes()) {
				percorridos += o.getNome().length();
			}
			for (Variante v : p.getVariantes()) {
				for (ValorOpcao valor : v.getValores()) {
					percorridos += valor.getValor().length() + valor.getOpcao().getNome().length();
				}
				percorridos += v.nomeCompleto().length();
			}
		}

		assertThat(catalogo).hasSize(6);
		assertThat(percorridos).isPositive();
		assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(4);
	}

}
