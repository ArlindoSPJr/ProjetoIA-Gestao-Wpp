package app.gestaoIAWpp.ai.tools.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

public record ConsultarEstoqueResponse(UUID varianteId, String nome,
        @JsonInclude(JsonInclude.Include.NON_NULL) String opcoes, boolean emEstoque, int quantidadeDisponivel) {
}
