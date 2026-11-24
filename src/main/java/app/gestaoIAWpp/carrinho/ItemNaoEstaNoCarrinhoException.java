package app.gestaoIAWpp.carrinho;

import java.util.UUID;

public class ItemNaoEstaNoCarrinhoException extends RuntimeException {

    public ItemNaoEstaNoCarrinhoException(UUID varianteId) {
        super("Variante " + varianteId + " não está no carrinho.");
    }
}
