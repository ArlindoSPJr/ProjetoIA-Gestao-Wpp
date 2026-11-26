package app.gestaoIAWpp.empresa;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API pública do domínio empresa: resolve a empresa dona de um número de atendimento. */
@Service
public class NumeroWhatsappService {

	private final NumeroWhatsappRepository numeros;

	public NumeroWhatsappService(NumeroWhatsappRepository numeros) {
		this.numeros = numeros;
	}

	/** @throws NumeroNaoCadastradoException se o número não existe ou está inativo */
	@Transactional(readOnly = true)
	public UUID empresaDoNumero(String phoneNumberId) {
		return numeros.findByPhoneNumberIdAndAtivoTrue(phoneNumberId)
				.orElseThrow(NumeroNaoCadastradoException::new)
				.getEmpresaId();
	}
}
