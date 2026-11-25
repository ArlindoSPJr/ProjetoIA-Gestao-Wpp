package app.gestaoIAWpp.carrinho;

import java.math.BigDecimal;
import java.util.List;

public record CarrinhoResumo(List<ItemCarrinhoResumo> itens, BigDecimal total) {
}
