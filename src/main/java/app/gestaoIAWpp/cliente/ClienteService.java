package app.gestaoIAWpp.cliente;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClienteService {

	private final ClienteRepository repository;

	public ClienteService(ClienteRepository repository) {
		this.repository = repository;
	}

	/** Insere o cliente se ainda não existe (atômico, sem corrida) e devolve o registro. */
	@Transactional
	public Cliente identificar(UUID empresaId, String userId, String telefone) {
		repository.inserirSeNaoExiste(empresaId, userId, telefone);
		return repository.findByEmpresaIdAndUserId(empresaId, userId).orElseThrow();
	}

	@Transactional(readOnly = true)
	public Optional<Cliente> buscar(UUID empresaId, String userId) {
		return repository.findByEmpresaIdAndUserId(empresaId, userId);
	}

}
