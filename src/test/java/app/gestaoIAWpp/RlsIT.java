package app.gestaoIAWpp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

import java.util.List;
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
class RlsIT {

	private static final String TABELAS_DO_PUBLIC = """
			SELECT c.relname
			FROM pg_class c
			JOIN pg_namespace n ON n.oid = c.relnamespace
			WHERE n.nspname = 'public' AND c.relkind = 'r'
			""";

	@Autowired
	JdbcTemplate jdbc;

	@Test
	@DisplayName("T29 toda tabela do public tem RLS ligado")
	void deveTerRlsEmTodasAsTabelas_quandoMigrationsAplicadas() {
		// guarda contra passar vazio por engano: a consulta sem filtro enxerga as tabelas
		List<String> todas = jdbc.queryForList(TABELAS_DO_PUBLIC, String.class);
		assertThat(todas).hasSizeGreaterThanOrEqualTo(11).contains("flyway_schema_history");

		List<String> semRls = jdbc.queryForList(TABELAS_DO_PUBLIC + " AND NOT c.relrowsecurity", String.class);
		assertThat(semRls).as("tabelas do public sem RLS").isEmpty();
	}
}
