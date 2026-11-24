package app.gestaoIAWpp.produto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Produto do catálogo de uma empresa. O que se vende é a {@link Variante} (preço e estoque); o produto
 * agrupa as variantes e declara as {@link Opcao opções} (ex.: Cor, Tamanho). As coleções são {@code Set}
 * para o Hibernate poder buscar várias de uma vez sem {@code MultipleBagFetchException}.
 */
@Entity
@Table(name = "produto")
public class Produto {

    @Id
    private UUID id;

    @Column(name = "empresa_id", nullable = false)
    private UUID empresaId;

    @Column(nullable = false)
    private String nome;

    private String descricao;

    private String categoria;

    @Column(nullable = false)
    private boolean ativo;

    @OneToMany(mappedBy = "produto")
    private Set<Opcao> opcoes = new LinkedHashSet<>();

    @OneToMany(mappedBy = "produto")
    private Set<Variante> variantes = new LinkedHashSet<>();

    protected Produto() {
    }

    Produto(UUID id, UUID empresaId, String nome, String descricao, String categoria, boolean ativo) {
        this.id = id;
        this.empresaId = empresaId;
        this.nome = nome;
        this.descricao = descricao;
        this.categoria = categoria;
        this.ativo = ativo;
    }

    void adicionarOpcao(Opcao opcao) {
        opcoes.add(opcao);
    }

    void adicionarVariante(Variante variante) {
        variantes.add(variante);
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmpresaId() {
        return empresaId;
    }

    public String getNome() {
        return nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getCategoria() {
        return categoria;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public Set<Opcao> getOpcoes() {
        return Collections.unmodifiableSet(opcoes);
    }

    public Set<Variante> getVariantes() {
        return Collections.unmodifiableSet(variantes);
    }
}
