package app.gestaoIAWpp.cliente;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ClienteRepository extends JpaRepository<Cliente, UUID> {

	@Modifying
	@Query(nativeQuery = true, value = """
			INSERT INTO cliente (empresa_id, user_id, telefone)
			VALUES (:empresaId, :userId, :telefone)
			ON CONFLICT (empresa_id, user_id) DO NOTHING
			""")
	int inserirSeNaoExiste(@Param("empresaId") UUID empresaId, @Param("userId") String userId,
			@Param("telefone") String telefone);

	Optional<Cliente> findByEmpresaIdAndUserId(UUID empresaId, String userId);

}
