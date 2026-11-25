package app.gestaoIAWpp.produto;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface VarianteRepository extends JpaRepository<Variante, UUID> {

    /** Variante ativa, de produto ativo da empresa, com produto, valores e opções carregados (RN06, RN07). */
    @Query("""
            select v from Variante v
            join fetch v.produto p
            left join fetch v.valores vo
            left join fetch vo.opcao
            where v.id = :varianteId and p.empresaId = :empresaId and v.ativo = true and p.ativo = true
            """)
    Optional<Variante> daEmpresa(@Param("varianteId") UUID varianteId, @Param("empresaId") UUID empresaId);

    /**
     * RN01: baixa atômica. Um único UPDATE condicionado ao saldo; 0 linhas afetadas = estoque insuficiente.
     * Limpa o contexto de persistência para leituras seguintes não verem o estoque antigo.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE variante SET quantidade_estoque = quantidade_estoque - :quantidade, atualizado_em = now()
            WHERE id = :varianteId AND quantidade_estoque >= :quantidade
            """)
    int baixarEstoque(@Param("varianteId") UUID varianteId, @Param("quantidade") int quantidade);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE variante SET quantidade_estoque = quantidade_estoque + :quantidade, atualizado_em = now()
            WHERE id = :varianteId
            """)
    int devolverEstoque(@Param("varianteId") UUID varianteId, @Param("quantidade") int quantidade);
}
