package app.gestaoIAWpp.produto;

import java.util.UUID;

public class EstoqueInsuficienteException extends RuntimeException {

    private final UUID varianteId;
    private final int solicitado;
    private final int disponivel;

    public EstoqueInsuficienteException(UUID varianteId, String nome, int solicitado, int disponivel) {
        super("Estoque insuficiente para " + nome + ": solicitado " + solicitado + ", disponível " + disponivel + ".");
        this.varianteId = varianteId;
        this.solicitado = solicitado;
        this.disponivel = disponivel;
    }

    public UUID getVarianteId() {
        return varianteId;
    }

    public int getSolicitado() {
        return solicitado;
    }

    public int getDisponivel() {
        return disponivel;
    }
}
