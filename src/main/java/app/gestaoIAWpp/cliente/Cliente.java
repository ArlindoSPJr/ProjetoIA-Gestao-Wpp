package app.gestaoIAWpp.cliente;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "cliente")
class Cliente {

	@Id
	private UUID id;

	@Column(name = "empresa_id", nullable = false)
	private UUID empresaId;

	@Column(name = "user_id", nullable = false)
	private String userId;

	private String telefone;

	protected Cliente() {
	}

	public UUID getId() {
		return id;
	}

	public UUID getEmpresaId() {
		return empresaId;
	}

	public String getUserId() {
		return userId;
	}

	public String getTelefone() {
		return telefone;
	}

}
