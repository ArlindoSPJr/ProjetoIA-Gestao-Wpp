package app.gestaoIAWpp.carrinho;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

/** Item do carrinho; {@code nome} é o nome completo da variante (RN10). */
public record ItemCarrinhoResumo(UUID varianteId, String nome, BigDecimal precoUnitario, int quantidade) {

    @JsonProperty("subtotal")
    public BigDecimal subtotal() {
        return precoUnitario.multiply(BigDecimal.valueOf(quantidade)).setScale(2);
    }
}
