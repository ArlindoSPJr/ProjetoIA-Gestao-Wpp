package app.gestaoIAWpp.carrinho;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CarrinhoRepository extends JpaRepository<Carrinho, UUID> {

    /**
     * RN04: cria o carrinho ABERTO do cliente se ainda não existe. O índice único parcial resolve a
     * corrida: a segunda inserção cai no ON CONFLICT e não faz nada. Devolve 1 se criou, 0 se já havia.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO carrinho (empresa_id, cliente_id, status)
            VALUES (:empresaId, :clienteId, 'ABERTO')
            ON CONFLICT (cliente_id) WHERE status = 'ABERTO' DO NOTHING
            """)
    int criarAbertoSeNaoExiste(@Param("empresaId") UUID empresaId, @Param("clienteId") UUID clienteId);

    Optional<Carrinho> findByClienteIdAndStatus(UUID clienteId, StatusCarrinho status);

    /**
     * RN05: upsert do item; se a variante já está no carrinho, soma a quantidade. Limpa o contexto de
     * persistência para o resumo seguinte ler o item gravado.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            INSERT INTO item_carrinho (carrinho_id, variante_id, quantidade)
            VALUES (:carrinhoId, :varianteId, :quantidade)
            ON CONFLICT (carrinho_id, variante_id)
            DO UPDATE SET quantidade = item_carrinho.quantidade + EXCLUDED.quantidade
            """)
    int somarItem(@Param("carrinhoId") UUID carrinhoId, @Param("varianteId") UUID varianteId,
            @Param("quantidade") int quantidade);

    /**
     * O carrinho ABERTO do cliente, travado ({@code SELECT ... FOR UPDATE}) até o fim da transação.
     * Quem altera itens e o reset passam por esta trava, então não se intercalam no mesmo carrinho.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Carrinho c where c.clienteId = :clienteId and c.status = :status")
    Optional<Carrinho> travarDoCliente(@Param("clienteId") UUID clienteId, @Param("status") StatusCarrinho status);

    /** RN14: os carrinhos da empresa no status dado, travados até o fim da transação (ver {@link #travarDoCliente}). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Carrinho c where c.empresaId = :empresaId and c.status = :status")
    List<Carrinho> travarDaEmpresa(@Param("empresaId") UUID empresaId, @Param("status") StatusCarrinho status);
}
