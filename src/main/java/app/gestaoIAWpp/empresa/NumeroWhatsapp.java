package app.gestaoIAWpp.empresa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "numero_whatsapp")
class NumeroWhatsapp {

	@Id
	private UUID id;

	@Column(name = "empresa_id", nullable = false)
	private UUID empresaId;

	@Column(name = "phone_number_id", nullable = false)
	private String phoneNumberId;

	@Column(nullable = false)
	private boolean ativo;

	protected NumeroWhatsapp() {
	}

	public UUID getId() {
		return id;
	}

	public UUID getEmpresaId() {
		return empresaId;
	}

	public String getPhoneNumberId() {
		return phoneNumberId;
	}

	public boolean isAtivo() {
		return ativo;
	}

}
