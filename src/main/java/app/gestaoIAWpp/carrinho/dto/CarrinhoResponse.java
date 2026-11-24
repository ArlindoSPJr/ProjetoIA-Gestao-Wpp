package app.gestaoIAWpp.carrinho.dto;

import java.math.BigDecimal;
import java.util.List;

public record CarrinhoResponse(List<ItemCarrinhoResponse> itens, BigDecimal total) {
}
