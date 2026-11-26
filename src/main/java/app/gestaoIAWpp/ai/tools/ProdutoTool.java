package app.gestaoIAWpp.ai.tools;

import app.gestaoIAWpp.ai.AgentTool;
import app.gestaoIAWpp.ai.ContextoTool;
import app.gestaoIAWpp.ai.tools.dto.ConsultarEstoqueResponse;
import app.gestaoIAWpp.ai.tools.dto.ConsultarProdutoResponse;
import app.gestaoIAWpp.ai.tools.dto.ProdutoResponse;
import app.gestaoIAWpp.ai.tools.dto.VarianteResponse;
import app.gestaoIAWpp.produto.DisponibilidadeVariante;
import app.gestaoIAWpp.produto.ProdutoService;
import app.gestaoIAWpp.produto.ResultadoBusca;
import app.gestaoIAWpp.produto.VarianteNaoEncontradaException;
import java.util.UUID;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Tools de consulta de produto e estoque expostas ao modelo; só delegam ao {@link ProdutoService}.
 * A empresa vem do {@link ToolContext}, nunca do modelo (RN06).
 */
@Component
@Order(1)
public class ProdutoTool implements AgentTool {

    public static final String DESCRICAO_VARIANTE_ID = "Id da variante retornado por consultar_produto.";
    private static final String DESCRICAO_CONSULTAR_PRODUTO = "Busca produtos da loja a partir do que o cliente pediu. Use sempre que o cliente perguntar "
            + "por um produto, preço ou disponibilidade, e antes de adicionar algo ao carrinho "
            + "quando ainda não tiver o id da variante. Faça uma busca por tipo de produto: se o cliente "
            + "pedir vários tipos (ex.: camisa e tênis), chame esta ferramenta uma vez para cada tipo. "
            + "Retorna os 5 produtos mais relevantes, cada um com todas as suas variantes (opções como "
            + "cor e tamanho, id da variante, preço e estoque). Se o resultado indicar que há mais "
            + "produtos, mostre as opções ao cliente e peça mais detalhes (modelo, cor, tamanho) para "
            + "refinar a busca.";
    private static final String DESCRICAO_CONSULTAR_ESTOQUE = "Consulta a quantidade disponível em estoque de uma variante já identificada. Use quando o "
            + "cliente perguntar se ainda há unidades de uma variante encontrada antes.";

    private final ProdutoService produtoService;

    public ProdutoTool(ProdutoService produtoService) {
        this.produtoService = produtoService;
    }

    /**
     * O modelo manda o id como texto. Texto que não é UUID (ex.: "P001", "") é tratado como variante
     * inexistente, com a mesma mensagem, em vez de vazar um erro de conversão.
     */
    public static UUID idDeVariante(String texto) {
        try {
            return UUID.fromString(String.valueOf(texto));
        } catch (IllegalArgumentException e) {
            throw new VarianteNaoEncontradaException(texto);
        }
    }

    @Tool(name = "consultar_produto", description = DESCRICAO_CONSULTAR_PRODUTO)
    public ConsultarProdutoResponse consultarProduto(
            @ToolParam(description = "Palavras-chave do produto extraídas da mensagem do cliente. "
                    + "Ex.: 'camisa preta', 'tênis branco'.") String termo,
            ToolContext ctx) {
        return paraResponse(produtoService.buscar(ContextoTool.empresaId(ctx), termo));
    }

    @Tool(name = "consultar_estoque", description = DESCRICAO_CONSULTAR_ESTOQUE)
    public ConsultarEstoqueResponse consultarEstoque(
            @ToolParam(description = DESCRICAO_VARIANTE_ID) String varianteId,
            ToolContext ctx) {
        DisponibilidadeVariante d = produtoService.consultarDisponibilidade(
                ContextoTool.empresaId(ctx), idDeVariante(varianteId));
        return new ConsultarEstoqueResponse(d.varianteId(), d.nome(), d.opcoes(), d.emEstoque(),
                d.quantidadeDisponivel());
    }

    private static ConsultarProdutoResponse paraResponse(ResultadoBusca r) {
        return new ConsultarProdutoResponse(
                r.produtos().stream()
                        .map(p -> new ProdutoResponse(p.produtoId(), p.nome(), p.descricao(),
                                p.variantes().stream()
                                        .map(v -> new VarianteResponse(v.varianteId(), v.opcoes(), v.preco(),
                                                v.emEstoque(), v.quantidadeDisponivel()))
                                        .toList()))
                        .toList(),
                r.totalEncontrado(), r.mensagem());
    }
}
