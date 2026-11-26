package app.gestaoIAWpp.empresa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NumeroWhatsappServiceTest {

	private static final UUID EMPRESA = UUID.randomUUID();
	private static final String NUMERO = "simulador-loja-demo";

	private final NumeroWhatsappRepository numeros = mock(NumeroWhatsappRepository.class);
	private final NumeroWhatsappService service = new NumeroWhatsappService(numeros);

	@Test
	void deveDevolverEmpresa_quandoNumeroAtivo() {
		NumeroWhatsapp numero = mock(NumeroWhatsapp.class);
		when(numero.getEmpresaId()).thenReturn(EMPRESA);
		when(numeros.findByPhoneNumberIdAndAtivoTrue(NUMERO)).thenReturn(Optional.of(numero));

		assertThat(service.empresaDoNumero(NUMERO)).isEqualTo(EMPRESA);
	}

	@Test
	void deveLancar_quandoEmpresaDoNumeroNaoCadastrado() {
		when(numeros.findByPhoneNumberIdAndAtivoTrue("x")).thenReturn(Optional.empty());

		assertThrows(NumeroNaoCadastradoException.class, () -> service.empresaDoNumero("x"));
	}
}
