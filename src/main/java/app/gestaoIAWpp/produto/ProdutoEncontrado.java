package app.gestaoIAWpp.produto;

/** Produto retornado por uma busca, com a pontuação de relevância para o termo buscado. */
public record ProdutoEncontrado(Produto produto, int pontuacao) {
}
