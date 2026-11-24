package app.gestaoIAWpp.simulador;

import java.math.BigDecimal;
import java.util.UUID;

/** Linha do painel de estoque do simulador; {@code nome} é o nome completo da variante. */
public record ItemEstoqueView(UUID varianteId, String nome, BigDecimal preco, int quantidade) {
}
