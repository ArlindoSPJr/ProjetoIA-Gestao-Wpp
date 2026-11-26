package app.gestaoIAWpp.carrinho;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Carrinho de um cliente numa empresa. É criado pelo SQL nativo do {@link CarrinhoRepository} (sem
 * corrida com o índice único parcial), por isso as datas são só lidas: o banco preenche o default.
 */
@Entity
@Table(name = "carrinho")
class Carrinho {

    @Id
    private UUID id;

    @Column(name = "empresa_id", nullable = false)
    private UUID empresaId;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusCarrinho status;

    @Column(name = "criado_em", insertable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", insertable = false, updatable = false)
    private OffsetDateTime atualizadoEm;

    /** {@code orphanRemoval}: tirar o item da lista apaga a linha no flush. */
    @OneToMany(mappedBy = "carrinho", orphanRemoval = true)
    @OrderBy("adicionadoEm")
    private List<ItemCarrinho> itens = new ArrayList<>();

    protected Carrinho() {
    }

    /** Para fixtures de teste; em produção o carrinho nasce no {@code criarAbertoSeNaoExiste}. */
    Carrinho(UUID id, UUID empresaId, UUID clienteId, StatusCarrinho status) {
        this.id = id;
        this.empresaId = empresaId;
        this.clienteId = clienteId;
        this.status = status;
    }

    public Optional<ItemCarrinho> item(UUID varianteId) {
        return itens.stream().filter(i -> i.getVarianteId().equals(varianteId)).findFirst();
    }

    void incluir(ItemCarrinho item) {
        itens.add(item);
    }

    void tirar(ItemCarrinho item) {
        itens.remove(item);
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmpresaId() {
        return empresaId;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public StatusCarrinho getStatus() {
        return status;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public OffsetDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public List<ItemCarrinho> getItens() {
        return Collections.unmodifiableList(itens);
    }
}
