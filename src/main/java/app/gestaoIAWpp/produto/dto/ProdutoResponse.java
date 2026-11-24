package app.gestaoIAWpp.produto.dto;

import java.util.List;
import java.util.UUID;

public record ProdutoResponse(UUID produtoId, String nome, String descricao, List<VarianteResponse> variantes) {
}
