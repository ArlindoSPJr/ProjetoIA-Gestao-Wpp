package app.gestaoIAWpp.ai.dto;

public record ConsultarEstoqueResponse(String produtoId, String nome, boolean emEstoque, int quantidadeDisponivel) {
}
