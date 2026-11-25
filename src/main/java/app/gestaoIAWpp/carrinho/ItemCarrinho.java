package app.gestaoIAWpp.carrinho;

import app.gestaoIAWpp.produto.Variante;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Uma variante no carrinho. Não guarda preço: o resumo usa o preço atual da variante (RN08). */
@Entity
@Table(name = "item_carrinho")
public class ItemCarrinho {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "carrinho_id", nullable = false)
    private Carrinho carrinho;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variante_id", nullable = false)
    private Variante variante;

    @Column(nullable = false)
    private int quantidade;

    @Column(name = "adicionado_em", insertable = false, updatable = false)
    private OffsetDateTime adicionadoEm;

    protected ItemCarrinho() {
    }

    /** Para fixtures de teste; em produção o item nasce no upsert {@code somarItem}. */
    ItemCarrinho(UUID id, Carrinho carrinho, Variante variante, int quantidade) {
        this.id = id;
        this.carrinho = carrinho;
        this.variante = variante;
        this.quantidade = quantidade;
    }

    /** Remoção parcial (RN03); quem chama garante {@code 0 < quantidade < this.quantidade}. */
    void diminuir(int quantidade) {
        this.quantidade -= quantidade;
    }

    /** Espelha o upsert do banco nas fixtures de teste. */
    void somar(int quantidade) {
        this.quantidade += quantidade;
    }

    public UUID getId() {
        return id;
    }

    public Carrinho getCarrinho() {
        return carrinho;
    }

    public Variante getVariante() {
        return variante;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public OffsetDateTime getAdicionadoEm() {
        return adicionadoEm;
    }
}
