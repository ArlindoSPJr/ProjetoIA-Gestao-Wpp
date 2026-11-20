package app.gestaoIAWpp.produto;

import java.math.BigDecimal;

public record Produto(String id, String nome, String descricao, String categoria, BigDecimal preco) {
}
