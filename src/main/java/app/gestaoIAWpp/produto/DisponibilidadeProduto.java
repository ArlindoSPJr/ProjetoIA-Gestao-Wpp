package app.gestaoIAWpp.produto;

public record DisponibilidadeProduto(String produtoId, String nome, boolean emEstoque, int quantidadeDisponivel) {
}
