package app.gestaoIAWpp.produto;

public class ProdutoNaoEncontradoException extends RuntimeException {

    public ProdutoNaoEncontradoException(String produtoId) {
        super("Produto " + produtoId + " não encontrado.");
    }
}
