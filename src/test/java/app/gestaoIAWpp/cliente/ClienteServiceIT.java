package app.gestaoIAWpp.cliente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

import app.gestaoIAWpp.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import({TestcontainersConfiguration.class, ClienteService.class})
class ClienteServiceIT {

	private static final String USER_ID = "BR.SIM5511999990000";

	@Autowired
	ClienteService service;

	@Autowired
	JdbcTemplate jdbc;

	private UUID empresaDoSeed() {
		return jdbc.queryForObject("SELECT id FROM empresa WHERE documento = '11222333000181'", UUID.class);
	}

	@Test
	@DisplayName("T08 cliente novo é criado e o segundo identificar devolve o mesmo")
	void deveCriarEReaproveitarCliente_quandoIdentificaDuasVezes() {
		UUID empresa = empresaDoSeed();

		Cliente primeiro = service.identificar(empresa, USER_ID, "5511999990000");
		Cliente segundo = service.identificar(empresa, USER_ID, "5511999990000");

		assertThat(segundo.getId()).isEqualTo(primeiro.getId());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM cliente WHERE user_id = ?", Integer.class, USER_ID))
				.isEqualTo(1);
	}

	@ParameterizedTest
	@ValueSource(strings = {"+5511999990000", "123456789", "1234567890123456"})
	@DisplayName("T28 telefone fora do formato é recusado")
	void deveRecusarTelefoneForaDoFormato_quandoInsereCliente(String telefone) {
		UUID empresa = empresaDoSeed();

		assertThatThrownBy(() -> service.identificar(empresa, "u-" + telefone, telefone))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {"1234567890", "123456789012345"})
	@DisplayName("T28 telefone nos limites 10 e 15 dígitos é aceito")
	void deveAceitarTelefoneNoLimite_quandoInsereCliente(String telefone) {
		UUID empresa = empresaDoSeed();

		Cliente cliente = service.identificar(empresa, "u-" + telefone, telefone);

		assertThat(cliente.getTelefone()).isEqualTo(telefone);
	}

	@Test
	@DisplayName("T28 documento de empresa com 13 dígitos é recusado")
	void deveRecusarDocumentoForaDoFormato_quandoInsereEmpresa() {
		assertThatThrownBy(() -> insereEmpresa("1234567890123"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("T28 documento de empresa com 11 dígitos é aceito")
	void deveAceitarDocumentoCpf_quandoInsereEmpresa() {
		assertThatCode(() -> insereEmpresa("12345678901")).doesNotThrowAnyException();
	}

	private void insereEmpresa(String documento) {
		jdbc.update("INSERT INTO empresa (razao_social, nome_fantasia, documento) VALUES ('R', 'N', ?)", documento);
	}

}
