package app.gestaoIAWpp.produto;

/** Variante inexistente, inativa, de produto inativo ou de outra empresa: para quem pergunta, é a mesma coisa. */
public class VarianteNaoEncontradaException extends RuntimeException {

    public VarianteNaoEncontradaException(String varianteId) {
        super("Variante " + varianteId + " não encontrada.");
    }
}
