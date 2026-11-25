package app.gestaoIAWpp.produto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Uma dimensão de variação do produto (ex.: Cor), com seus valores possíveis. */
@Entity
@Table(name = "opcao")
public class Opcao {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private int posicao;

    @OneToMany(mappedBy = "opcao")
    private Set<ValorOpcao> valores = new LinkedHashSet<>();

    protected Opcao() {
    }

    Opcao(UUID id, Produto produto, String nome, int posicao) {
        this.id = id;
        this.produto = produto;
        this.nome = nome;
        this.posicao = posicao;
    }

    void adicionarValor(ValorOpcao valor) {
        valores.add(valor);
    }

    public UUID getId() {
        return id;
    }

    public Produto getProduto() {
        return produto;
    }

    public String getNome() {
        return nome;
    }

    public int getPosicao() {
        return posicao;
    }

    public Set<ValorOpcao> getValores() {
        return Collections.unmodifiableSet(valores);
    }
}
