package app.gestaoIAWpp.produto.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.UUID;

public record VarianteResponse(UUID varianteId, @JsonInclude(JsonInclude.Include.NON_NULL) String opcoes,
        BigDecimal preco, boolean emEstoque, int quantidadeDisponivel) {
}
