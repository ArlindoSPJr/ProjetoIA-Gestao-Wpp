package app.gestaoIAWpp.carrinho;

import app.gestaoIAWpp.ai.AgentTool;
import app.gestaoIAWpp.ai.ContextoTool;
import app.gestaoIAWpp.ai.dto.CarrinhoResponse;
import app.gestaoIAWpp.ai.dto.ItemCarrinhoResponse;
import app.gestaoIAWpp.produto.ProdutoTool;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Tools de carrinho expostas ao modelo; o cliente vem do {@link ToolContext}, nunca do modelo. */
@Component
@Order(2)
public class CarrinhoTool implements AgentTool {

    private static final String DESCRICAO_ADICIONAR = "Adiciona um produto ao carrinho do cliente e reserva o estoque. Use quando o cliente já "
            + "tiver dito claramente qual produto e quantas unidades quer; se houver mais de uma "
            + "opção possível ou a quantidade não estiver clara, pergunte antes. Use o id "
            + "retornado por consultar_produto; nunca invente ids.";
    private static final String DESCRICAO_REMOVER = "Remove um produto do carrinho e devolve as unidades ao estoque. Sem quantidade, remove "
            + "todas as unidades daquele produto.";

    private final CarrinhoService carrinho;

    public CarrinhoTool(CarrinhoService carrinho) {
        this.carrinho = carrinho;
    }

    @Tool(name = "adicionar_ao_carrinho", description = DESCRICAO_ADICIONAR)
    public CarrinhoResponse adicionarAoCarrinho(
            @ToolParam(description = ProdutoTool.DESCRICAO_PRODUTO_ID) String produtoId,
            @ToolParam(description = "Quantidade a adicionar, no mínimo 1.") Integer quantidade,
            ToolContext ctx) {
        if (quantidade == null) {
            throw new IllegalArgumentException("Parâmetro obrigatório ausente: quantidade.");
        }
        return paraResponse(carrinho.adicionar(ContextoTool.clienteId(ctx).toString(), produtoId, quantidade));
    }

    @Tool(name = "remover_do_carrinho", description = DESCRICAO_REMOVER)
    public CarrinhoResponse removerDoCarrinho(
            @ToolParam(description = ProdutoTool.DESCRICAO_PRODUTO_ID) String produtoId,
            @ToolParam(description = "Quantidade a remover. Omita para remover tudo.", required = false)
            Integer quantidade,
            ToolContext ctx) {
        return paraResponse(carrinho.remover(ContextoTool.clienteId(ctx).toString(), produtoId, quantidade));
    }

    @Tool(name = "ver_carrinho",
            description = "Mostra os itens do carrinho do cliente e o total.")
    public CarrinhoResponse verCarrinho(ToolContext ctx) {
        return paraResponse(carrinho.ver(ContextoTool.clienteId(ctx).toString()));
    }

    private static CarrinhoResponse paraResponse(CarrinhoResumo r) {
        return new CarrinhoResponse(
                r.itens().stream()
                        .map(i -> new ItemCarrinhoResponse(i.produtoId(), i.nome(), i.precoUnitario(),
                                i.quantidade(), i.subtotal()))
                        .toList(),
                r.total());
    }
}
