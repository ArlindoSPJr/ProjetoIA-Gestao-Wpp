package app.gestaoIAWpp.produto;

import java.math.BigDecimal;

/** Produto como aparece numa busca: dados do catálogo mais a disponibilidade atual. */
public record ProdutoResumo(String id, String nome, String descricao, BigDecimal preco,
                            boolean emEstoque, int quantidadeDisponivel) {
}
