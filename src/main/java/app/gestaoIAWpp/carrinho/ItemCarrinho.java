package app.gestaoIAWpp.carrinho;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record ItemCarrinho(String produtoId, String nome, BigDecimal precoUnitario, int quantidade) {

    @JsonProperty("subtotal")
    public BigDecimal subtotal() {
        return precoUnitario.multiply(BigDecimal.valueOf(quantidade)).setScale(2);
    }
}
