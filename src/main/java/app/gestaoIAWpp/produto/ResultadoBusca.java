package app.gestaoIAWpp.produto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Resultado de uma busca de produtos; {@code mensagem} só existe quando não há resultado ou há excesso. */
public record ResultadoBusca(List<ProdutoResumo> produtos, int totalEncontrado,
                             @JsonInclude(JsonInclude.Include.NON_NULL) String mensagem) {
}
