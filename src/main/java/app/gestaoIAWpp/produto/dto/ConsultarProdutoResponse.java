package app.gestaoIAWpp.produto.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record ConsultarProdutoResponse(List<ProdutoResponse> produtos, int totalEncontrado,
        @JsonInclude(JsonInclude.Include.NON_NULL) String mensagem) {
}
