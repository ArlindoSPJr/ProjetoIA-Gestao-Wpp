package app.gestaoIAWpp.produto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Variante como aparece numa busca ou no painel do simulador.
 *
 * @param nome   nome completo (RN10), ex.: {@code "Camisa Básica (Preta / M)"}
 * @param opcoes {@code "Cor: Preta, Tamanho: M"}, ou {@code null} se o produto não tem opções
 */
public record VarianteResumo(UUID varianteId, String nome, String opcoes, BigDecimal preco, boolean emEstoque,
        int quantidadeDisponivel) {

    static VarianteResumo de(Variante v) {
        return new VarianteResumo(v.getId(), v.nomeCompleto(), v.opcoesDescritas(), v.getPreco(), v.emEstoque(),
                v.getQuantidadeEstoque());
    }
}
