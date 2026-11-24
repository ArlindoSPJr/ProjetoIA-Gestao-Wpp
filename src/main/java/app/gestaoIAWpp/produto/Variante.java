package app.gestaoIAWpp.produto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** O item vendável: uma combinação de valores de opção do produto, com preço e estoque próprios. */
@Entity
@Table(name = "variante")
public class Variante {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    private String sku;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal preco;

    @Column(name = "quantidade_estoque", nullable = false)
    private int quantidadeEstoque;

    @Column(nullable = false)
    private boolean ativo;

    @ManyToMany
    @JoinTable(name = "variante_valor",
            joinColumns = @JoinColumn(name = "variante_id"),
            inverseJoinColumns = @JoinColumn(name = "valor_opcao_id"))
    private Set<ValorOpcao> valores = new LinkedHashSet<>();

    protected Variante() {
    }

    Variante(UUID id, Produto produto, String sku, BigDecimal preco, int quantidadeEstoque, boolean ativo,
            Set<ValorOpcao> valores) {
        this.id = id;
        this.produto = produto;
        this.sku = sku;
        this.preco = preco;
        this.quantidadeEstoque = quantidadeEstoque;
        this.ativo = ativo;
        this.valores = new LinkedHashSet<>(valores);
    }

    /** RN10: {@code "Cor: Preta, Tamanho: M"} na ordem das opções; {@code null} se não há valores. */
    public String opcoesDescritas() {
        if (valores.isEmpty()) {
            return null;
        }
        return valoresOrdenados().stream()
                .map(v -> v.getOpcao().getNome() + ": " + v.getValor())
                .collect(Collectors.joining(", "));
    }

    /** RN10: {@code "Camisa Básica (Preta / M)"}, ou só o nome do produto se não há valores. */
    public String nomeCompleto() {
        if (valores.isEmpty()) {
            return produto.getNome();
        }
        return valoresOrdenados().stream()
                .map(ValorOpcao::getValor)
                .collect(Collectors.joining(" / ", produto.getNome() + " (", ")"));
    }

    /** Os valores na ordem das opções do produto ({@code opcao.posicao}). */
    public List<ValorOpcao> valoresOrdenados() {
        return valores.stream()
                .sorted(Comparator.comparingInt(v -> v.getOpcao().getPosicao()))
                .toList();
    }

    public boolean emEstoque() {
        return quantidadeEstoque > 0;
    }

    public UUID getId() {
        return id;
    }

    public Produto getProduto() {
        return produto;
    }

    public String getSku() {
        return sku;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public int getQuantidadeEstoque() {
        return quantidadeEstoque;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public Set<ValorOpcao> getValores() {
        return Collections.unmodifiableSet(valores);
    }
}
