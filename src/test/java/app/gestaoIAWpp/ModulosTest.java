package app.gestaoIAWpp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.Violations;
import org.springframework.modulith.docs.Documenter;

import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Verifica as fronteiras entre os módulos da aplicação. É teste unitário: sem contexto Spring e sem banco.
 */
class ModulosTest {

	private static final Path PASTA_DIAGRAMA = Path.of("docs", "arquitetura");

	// O Documenter escreve cada módulo como componente com o nome capitalizado (ex.: "Ai", $techn="Module").
	private static final List<String> COMPONENTES = List.of(
			"Empresa", "Cliente", "Produto", "Carrinho", "Ai", "Atendimento", "Simulador").stream()
			.map(nome -> "\"" + nome + "\", $techn=\"Module\"")
			.toList();

	@Test
	void deveVerificarModulos_quandoFronteirasRespeitadas() {
		ApplicationModules modulos = ApplicationModules.of(GestaoIaWppApplication.class);

		assertThatCode(modulos::verify).doesNotThrowAnyException();
	}

	@Test
	void deveFalhar_quandoModuloUsaDependenciaNaoDeclarada() {
		// A fixture vive em src/test, que o Modulith ignora por padrão; este ImportOption aceita todas as classes.
		ImportOption incluirTudo = localizacao -> true;
		ApplicationModules modulos = ApplicationModules.of("app.gestaoIAWpp.fixture.invalida", incluirTudo);

		assertThatThrownBy(modulos::verify)
				.isInstanceOf(Violations.class)
				.hasMessageContaining("origem")
				.hasMessageContaining("destino")
				.hasMessageContaining("ServicoDestino");
	}

	@Test
	void deveGerarDiagrama_quandoModulosVerificados() throws IOException {
		ApplicationModules modulos = ApplicationModules.of(GestaoIaWppApplication.class).verify();

		new Documenter(modulos, Documenter.Options.defaults().withOutputFolder(PASTA_DIAGRAMA.toString()))
				.writeModulesAsPlantUml();

		Path visaoGeral = PASTA_DIAGRAMA.resolve("components.puml");
		assertThat(visaoGeral).exists();
		assertThat(Files.readString(visaoGeral)).contains(COMPONENTES);
	}
}
