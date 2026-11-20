package app.gestaoIAWpp.produto;

public class EstoqueInsuficienteException extends RuntimeException {

    private final String produtoId;
    private final int solicitado;
    private final int disponivel;

    public EstoqueInsuficienteException(String produtoId, int solicitado, int disponivel) {
        super("Estoque insuficiente para " + produtoId + ": solicitado " + solicitado + ", disponível " + disponivel + ".");
        this.produtoId = produtoId;
        this.solicitado = solicitado;
        this.disponivel = disponivel;
    }

    public String getProdutoId() {
        return produtoId;
    }

    public int getSolicitado() {
        return solicitado;
    }

    public int getDisponivel() {
        return disponivel;
    }
}
