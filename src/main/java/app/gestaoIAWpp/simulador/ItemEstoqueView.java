package app.gestaoIAWpp.simulador;

import java.math.BigDecimal;

public record ItemEstoqueView(String produtoId, String nome, BigDecimal preco, int quantidade) {
}
