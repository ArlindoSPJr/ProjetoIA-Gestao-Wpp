package app.gestaoIAWpp.produto;

import java.util.List;
import java.util.UUID;

/** Produto como aparece numa busca, com todas as suas variantes ativas. */
public record ProdutoResumo(UUID produtoId, String nome, String descricao, List<VarianteResumo> variantes) {
}
