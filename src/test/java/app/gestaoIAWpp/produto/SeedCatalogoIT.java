package app.gestaoIAWpp.produto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

import app.gestaoIAWpp.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
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
class SeedCatalogoIT {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	@DisplayName("T02 cada variante tem exatamente um valor por opção do produto")
	void deveTerUmValorPorOpcao_quandoSeedAplicado() {
		// (a) par (variante, opção do produto) com contagem de valores daquela opção diferente de 1
		List<Map<String, Object>> contagemErrada = jdbc.queryForList("""
				SELECT v.id AS variante_id, o.id AS opcao_id
				FROM variante v
				JOIN opcao o ON o.produto_id = v.produto_id
				LEFT JOIN variante_valor vv ON vv.variante_id = v.id
				LEFT JOIN valor_opcao vo ON vo.id = vv.valor_opcao_id AND vo.opcao_id = o.id
				GROUP BY v.id, o.id
				HAVING count(vo.id) <> 1
				""");
		assertThat(contagemErrada).isEmpty();

		// (b) variante_valor cujo valor pertence a opção de outro produto
		List<Map<String, Object>> deOutroProduto = jdbc.queryForList("""
				SELECT vv.variante_id, vv.valor_opcao_id
				FROM variante_valor vv
				JOIN variante v ON v.id = vv.variante_id
				JOIN valor_opcao vo ON vo.id = vv.valor_opcao_id
				JOIN opcao o ON o.id = vo.opcao_id
				WHERE o.produto_id <> v.produto_id
				""");
		assertThat(deOutroProduto).isEmpty();

		// (c) variante de produto com opções e sem nenhuma linha em variante_valor
		List<Map<String, Object>> semValores = jdbc.queryForList("""
				SELECT v.id
				FROM variante v
				WHERE EXISTS (SELECT 1 FROM opcao o WHERE o.produto_id = v.produto_id)
				  AND NOT EXISTS (SELECT 1 FROM variante_valor vv WHERE vv.variante_id = v.id)
				""");
		assertThat(semValores).isEmpty();
	}

}
