package app.gestaoIAWpp.carrinho;

import java.math.BigDecimal;
import java.util.List;

public record CarrinhoResumo(List<ItemCarrinho> itens, BigDecimal total) {
}
