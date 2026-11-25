package app.gestaoIAWpp.produto;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProdutoRepository extends JpaRepository<Produto, UUID> {

    /**
     * Produtos ativos da empresa com opções, variantes, valores e a opção de cada valor carregados numa
     * única consulta (sem N+1). Variantes inativas vêm na coleção: o filtro fica no {@link ProdutoService}.
     */
    @Query("""
            select p from Produto p
            left join fetch p.opcoes
            left join fetch p.variantes v
            left join fetch v.valores vo
            left join fetch vo.opcao
            where p.empresaId = :empresaId and p.ativo = true
            """)
    List<Produto> catalogoAtivo(@Param("empresaId") UUID empresaId);
}
