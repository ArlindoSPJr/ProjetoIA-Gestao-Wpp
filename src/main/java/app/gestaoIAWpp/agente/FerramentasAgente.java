package app.gestaoIAWpp.agente;

import app.gestaoIAWpp.carrinho.CarrinhoResumo;
import app.gestaoIAWpp.carrinho.CarrinhoService;
import app.gestaoIAWpp.produto.DisponibilidadeProduto;
import app.gestaoIAWpp.produto.ProdutoService;
import app.gestaoIAWpp.produto.ResultadoBusca;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Tools expostas ao modelo (via {@code @Tool}); o Spring AI gera os schemas e executa as chamadas.
 * Preço, estoque e telefone vêm sempre do código: o modelo só informa ids, termo e quantidade.
 */
@Component
public class FerramentasAgente {

    public static final String CHAVE_TELEFONE = "telefone";

    private static final String DESCRICAO_PRODUTO_ID = "Id do produto retornado por consultar_produto.";
    private static final String DESCRICAO_CONSULTAR_PRODUTO = "Busca produtos da loja a partir do que o cliente pediu. Use sempre que o cliente perguntar "
            + "por um produto, preço ou disponibilidade, e antes de adicionar algo ao carrinho "
            + "quando ainda não tiver o id. Faça uma busca por tipo de produto: se o cliente pedir "
            + "vários tipos (ex.: camisa e tênis), chame esta ferramenta uma vez para cada tipo. "
            + "Retorna os 5 produtos mais relevantes com id, preço e estoque. Se o resultado "
            + "indicar que há mais produtos, mostre as opções ao cliente e peça mais detalhes "
            + "(modelo, cor, tamanho) para refinar a busca.";
    private static final String DESCRICAO_CONSULTAR_ESTOQUE = "Consulta a quantidade disponível em estoque de um produto já identificado. Use quando o "
            + "cliente perguntar se ainda há unidades de um produto encontrado antes.";
    private static final String DESCRICAO_ADICIONAR = "Adiciona um produto ao carrinho do cliente e reserva o estoque. Use quando o cliente já "
            + "tiver dito claramente qual produto e quantas unidades quer; se houver mais de uma "
            + "opção possível ou a quantidade não estiver clara, pergunte antes. Use o id "
            + "retornado por consultar_produto; nunca invente ids.";
    private static final String DESCRICAO_REMOVER = "Remove um produto do carrinho e devolve as unidades ao estoque. Sem quantidade, remove "
            + "todas as unidades daquele produto.";

    private final ProdutoService produtoService;
    private final CarrinhoService carrinho;

    public FerramentasAgente(ProdutoService produtoService, CarrinhoService carrinho) {
        this.produtoService = produtoService;
        this.carrinho = carrinho;
    }

    @Tool(name = "consultar_produto", description = DESCRICAO_CONSULTAR_PRODUTO)
    public ResultadoBusca consultarProduto(
            @ToolParam(description = "Palavras-chave do produto extraídas da mensagem do cliente. "
                    + "Ex.: 'camisa preta', 'tênis branco'.") String termo) {
        return produtoService.buscar(termo);
    }

    @Tool(name = "consultar_estoque", description = DESCRICAO_CONSULTAR_ESTOQUE)
    public DisponibilidadeProduto consultarEstoque(
            @ToolParam(description = DESCRICAO_PRODUTO_ID) String produtoId) {
        return produtoService.consultarDisponibilidade(produtoId);
    }

    @Tool(name = "adicionar_ao_carrinho", description = DESCRICAO_ADICIONAR)
    public CarrinhoResumo adicionarAoCarrinho(
            @ToolParam(description = DESCRICAO_PRODUTO_ID) String produtoId,
            @ToolParam(description = "Quantidade a adicionar, no mínimo 1.") Integer quantidade,
            ToolContext ctx) {
        if (quantidade == null) {
            throw new IllegalArgumentException("Parâmetro obrigatório ausente: quantidade.");
        }
        return carrinho.adicionar(telefone(ctx), produtoId, quantidade);
    }

    @Tool(name = "remover_do_carrinho", description = DESCRICAO_REMOVER)
    public CarrinhoResumo removerDoCarrinho(
            @ToolParam(description = DESCRICAO_PRODUTO_ID) String produtoId,
            @ToolParam(description = "Quantidade a remover. Omita para remover tudo.", required = false)
            Integer quantidade,
            ToolContext ctx) {
        return carrinho.remover(telefone(ctx), produtoId, quantidade);
    }

    @Tool(name = "ver_carrinho",
            description = "Mostra os itens do carrinho do cliente e o total.")
    public CarrinhoResumo verCarrinho(ToolContext ctx) {
        return carrinho.ver(telefone(ctx));
    }

    private static String telefone(ToolContext ctx) {
        Object telefone = ctx == null ? null : ctx.getContext().get(CHAVE_TELEFONE);
        if (telefone == null) {
            throw new IllegalStateException("Telefone ausente no contexto da ferramenta.");
        }
        return telefone.toString();
    }
}
