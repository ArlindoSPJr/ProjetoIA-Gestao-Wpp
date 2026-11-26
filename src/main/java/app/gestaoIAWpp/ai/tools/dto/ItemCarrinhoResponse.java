package app.gestaoIAWpp.ai.tools.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemCarrinhoResponse(UUID varianteId, String nome, BigDecimal precoUnitario, int quantidade,
        BigDecimal subtotal) {
}
