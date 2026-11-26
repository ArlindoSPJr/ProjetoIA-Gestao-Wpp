package app.gestaoIAWpp.produto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/** Um valor de uma opção (ex.: Preta, da opção Cor). */
@Entity
@Table(name = "valor_opcao")
class ValorOpcao {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opcao_id", nullable = false)
    private Opcao opcao;

    @Column(nullable = false)
    private String valor;

    @Column(nullable = false)
    private int posicao;

    protected ValorOpcao() {
    }

    ValorOpcao(UUID id, Opcao opcao, String valor, int posicao) {
        this.id = id;
        this.opcao = opcao;
        this.valor = valor;
        this.posicao = posicao;
    }

    public UUID getId() {
        return id;
    }

    public Opcao getOpcao() {
        return opcao;
    }

    public String getValor() {
        return valor;
    }

    public int getPosicao() {
        return posicao;
    }
}
