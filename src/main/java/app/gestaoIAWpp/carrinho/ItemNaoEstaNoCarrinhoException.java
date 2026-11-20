package app.gestaoIAWpp.carrinho;

public class ItemNaoEstaNoCarrinhoException extends RuntimeException {

    public ItemNaoEstaNoCarrinhoException(String produtoId) {
        super("Produto " + produtoId + " não está no carrinho.");
    }
}
