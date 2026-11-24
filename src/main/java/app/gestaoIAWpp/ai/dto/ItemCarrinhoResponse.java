package app.gestaoIAWpp.ai.dto;

import java.math.BigDecimal;

public record ItemCarrinhoResponse(String produtoId, String nome, BigDecimal precoUnitario, int quantidade,
        BigDecimal subtotal) {
}
