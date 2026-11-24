package app.gestaoIAWpp.ai.dto;

import java.math.BigDecimal;

public record ProdutoResponse(String id, String nome, String descricao, BigDecimal preco, boolean emEstoque,
        int quantidadeDisponivel) {
}
