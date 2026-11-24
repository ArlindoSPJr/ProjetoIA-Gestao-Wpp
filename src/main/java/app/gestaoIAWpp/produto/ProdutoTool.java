package app.gestaoIAWpp.produto;

import app.gestaoIAWpp.ai.AgentTool;
import app.gestaoIAWpp.ai.dto.ConsultarEstoqueResponse;
import app.gestaoIAWpp.ai.dto.ConsultarProdutoResponse;
import app.gestaoIAWpp.ai.dto.ProdutoResponse;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Tools de consulta de produto e estoque expostas ao modelo; só delegam ao {@link ProdutoService}. */
@Component
@Order(1)
public class ProdutoTool implements AgentTool {

    public static final String DESCRICAO_PRODUTO_ID = "Id do produto retornado por consultar_produto.";
    private static final String DESCRICAO_CONSULTAR_PRODUTO = "Busca produtos da loja a partir do que o cliente pediu. Use sempre que o cliente perguntar "
            + "por um produto, preço ou disponibilidade, e antes de adicionar algo ao carrinho "
            + "quando ainda não tiver o id. Faça uma busca por tipo de produto: se o cliente pedir "
            + "vários tipos (ex.: camisa e tênis), chame esta ferramenta uma vez para cada tipo. "
            + "Retorna os 5 produtos mais relevantes com id, preço e estoque. Se o resultado "
            + "indicar que há mais produtos, mostre as opções ao cliente e peça mais detalhes "
            + "(modelo, cor, tamanho) para refinar a busca.";
    private static final String DESCRICAO_CONSULTAR_ESTOQUE = "Consulta a quantidade disponível em estoque de um produto já identificado. Use quando o "
            + "cliente perguntar se ainda há unidades de um produto encontrado antes.";

    private final ProdutoService produtoService;

    public ProdutoTool(ProdutoService produtoService) {
        this.produtoService = produtoService;
    }

    @Tool(name = "consultar_produto", description = DESCRICAO_CONSULTAR_PRODUTO)
    public ConsultarProdutoResponse consultarProduto(
            @ToolParam(description = "Palavras-chave do produto extraídas da mensagem do cliente. "
                    + "Ex.: 'camisa preta', 'tênis branco'.") String termo) {
        return paraResponse(produtoService.buscar(termo));
    }

    @Tool(name = "consultar_estoque", description = DESCRICAO_CONSULTAR_ESTOQUE)
    public ConsultarEstoqueResponse consultarEstoque(
            @ToolParam(description = DESCRICAO_PRODUTO_ID) String produtoId) {
        DisponibilidadeProduto d = produtoService.consultarDisponibilidade(produtoId);
        return new ConsultarEstoqueResponse(d.produtoId(), d.nome(), d.emEstoque(), d.quantidadeDisponivel());
    }

    private static ConsultarProdutoResponse paraResponse(ResultadoBusca r) {
        return new ConsultarProdutoResponse(
                r.produtos().stream()
                        .map(p -> new ProdutoResponse(p.id(), p.nome(), p.descricao(), p.preco(), p.emEstoque(),
                                p.quantidadeDisponivel()))
                        .toList(),
                r.totalEncontrado(), r.mensagem());
    }
}
