package app.gestaoIAWpp.agente;

import app.gestaoIAWpp.carrinho.CarrinhoResumo;
import app.gestaoIAWpp.carrinho.CarrinhoService;
import app.gestaoIAWpp.produto.DisponibilidadeProduto;
import app.gestaoIAWpp.produto.ProdutoService;
import app.gestaoIAWpp.produto.ResultadoBusca;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Definições das tools expostas ao modelo e despacho da execução por nome.
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
    private static final String MENSAGEM_ERRO_PADRAO = "Erro inesperado ao executar a ferramenta.";
    private static final String JSON_ERRO_FIXO = "{\"erro\":\"" + MENSAGEM_ERRO_PADRAO + "\"}";
    private static final String ERRO_QUANTIDADE =
            "Parâmetro inválido: quantidade deve ser um número inteiro maior que zero.";

    private final ProdutoService produtoService;
    private final CarrinhoService carrinho;
    private final JsonMapper mapper;

    public FerramentasAgente(ProdutoService produtoService, CarrinhoService carrinho, JsonMapper mapper) {
        this.produtoService = produtoService;
        this.carrinho = carrinho;
        this.mapper = mapper;
    }

    @org.springframework.ai.tool.annotation.Tool(name = "consultar_produto", description = DESCRICAO_CONSULTAR_PRODUTO)
    public ResultadoBusca consultarProduto(
            @ToolParam(description = "Palavras-chave do produto extraídas da mensagem do cliente. "
                    + "Ex.: 'camisa preta', 'tênis branco'.") String termo) {
        return produtoService.buscar(termo);
    }

    @org.springframework.ai.tool.annotation.Tool(name = "consultar_estoque", description = DESCRICAO_CONSULTAR_ESTOQUE)
    public DisponibilidadeProduto consultarEstoque(
            @ToolParam(description = DESCRICAO_PRODUTO_ID) String produtoId) {
        return produtoService.consultarDisponibilidade(produtoId);
    }

    @org.springframework.ai.tool.annotation.Tool(name = "adicionar_ao_carrinho", description = DESCRICAO_ADICIONAR)
    public CarrinhoResumo adicionarAoCarrinho(
            @ToolParam(description = DESCRICAO_PRODUTO_ID) String produtoId,
            @ToolParam(description = "Quantidade a adicionar, no mínimo 1.") Integer quantidade,
            ToolContext ctx) {
        if (quantidade == null) {
            throw new IllegalArgumentException("Parâmetro obrigatório ausente: quantidade.");
        }
        return carrinho.adicionar(telefone(ctx), produtoId, quantidade);
    }

    @org.springframework.ai.tool.annotation.Tool(name = "remover_do_carrinho", description = DESCRICAO_REMOVER)
    public CarrinhoResumo removerDoCarrinho(
            @ToolParam(description = DESCRICAO_PRODUTO_ID) String produtoId,
            @ToolParam(description = "Quantidade a remover. Omita para remover tudo.", required = false)
            Integer quantidade,
            ToolContext ctx) {
        return carrinho.remover(telefone(ctx), produtoId, quantidade);
    }

    @org.springframework.ai.tool.annotation.Tool(name = "ver_carrinho",
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

    public List<Tool> definicoes() {
        return List.of(
                tool("consultar_produto",
                        "Busca produtos da loja a partir do que o cliente pediu. Use sempre que o cliente perguntar "
                                + "por um produto, preço ou disponibilidade, e antes de adicionar algo ao carrinho "
                                + "quando ainda não tiver o id. Faça uma busca por tipo de produto: se o cliente pedir "
                                + "vários tipos (ex.: camisa e tênis), chame esta ferramenta uma vez para cada tipo. "
                                + "Retorna os 5 produtos mais relevantes com id, preço e estoque. Se o resultado "
                                + "indicar que há mais produtos, mostre as opções ao cliente e peça mais detalhes "
                                + "(modelo, cor, tamanho) para refinar a busca.",
                        propriedades(
                                "termo", propriedade("string",
                                        "Palavras-chave do produto extraídas da mensagem do cliente. "
                                                + "Ex.: 'camisa preta', 'tênis branco'.")),
                        List.of("termo")),
                tool("consultar_estoque",
                        "Consulta a quantidade disponível em estoque de um produto já identificado. Use quando o "
                                + "cliente perguntar se ainda há unidades de um produto encontrado antes.",
                        propriedades("produto_id", propriedade("string", DESCRICAO_PRODUTO_ID)),
                        List.of("produto_id")),
                tool("adicionar_ao_carrinho",
                        "Adiciona um produto ao carrinho do cliente e reserva o estoque. Use quando o cliente já "
                                + "tiver dito claramente qual produto e quantas unidades quer; se houver mais de uma "
                                + "opção possível ou a quantidade não estiver clara, pergunte antes. Use o id "
                                + "retornado por consultar_produto; nunca invente ids.",
                        propriedades(
                                "produto_id", propriedade("string", DESCRICAO_PRODUTO_ID),
                                "quantidade", propriedade("integer", "Quantidade a adicionar, no mínimo 1.")),
                        List.of("produto_id", "quantidade")),
                tool("remover_do_carrinho",
                        "Remove um produto do carrinho e devolve as unidades ao estoque. Sem quantidade, remove "
                                + "todas as unidades daquele produto.",
                        propriedades(
                                "produto_id", propriedade("string", DESCRICAO_PRODUTO_ID),
                                "quantidade", propriedade("integer", "Quantidade a remover. Omita para remover tudo.")),
                        List.of("produto_id")),
                tool("ver_carrinho",
                        "Mostra os itens do carrinho do cliente e o total.",
                        propriedades(),
                        List.of()));
    }

    /** Nunca lança exceção: qualquer falha vira {"erro": "..."} com erro=true. */
    public ResultadoFerramenta executar(String telefone, String nome, Map<String, Object> entrada) {
        try {
            var ctx = new ToolContext(Map.of(CHAVE_TELEFONE, telefone));
            Object resultado = switch (nome) {
                case "consultar_produto" -> consultarProduto(texto(entrada, "termo"));
                case "consultar_estoque" -> consultarEstoque(texto(entrada, "produto_id"));
                case "adicionar_ao_carrinho" -> {
                    String id = texto(entrada, "produto_id");
                    yield adicionarAoCarrinho(id, quantidade(entrada, true), ctx);
                }
                case "remover_do_carrinho" -> {
                    String id = texto(entrada, "produto_id");
                    yield removerDoCarrinho(id, quantidade(entrada, false), ctx);
                }
                case "ver_carrinho" -> verCarrinho(ctx);
                default -> throw new IllegalArgumentException("Ferramenta desconhecida: " + nome + ".");
            };
            return new ResultadoFerramenta(mapper.writeValueAsString(resultado), false);
        } catch (Exception e) {
            return erro(e.getMessage());
        }
    }

    private String texto(Map<String, Object> entrada, String chave) {
        Object valor = entrada.get(chave);
        if (valor == null) {
            throw new IllegalArgumentException("Parâmetro obrigatório ausente: " + chave + ".");
        }
        return valor.toString();
    }

    /** Aceita só Integer ou Long >= 1. Devolve null quando opcional e ausente. */
    private Integer quantidade(Map<String, Object> entrada, boolean obrigatoria) {
        Object valor = entrada.get("quantidade");
        if (valor == null) {
            if (obrigatoria) {
                throw new IllegalArgumentException("Parâmetro obrigatório ausente: quantidade.");
            }
            return null;
        }
        if (valor instanceof Integer || valor instanceof Long) {
            long q = ((Number) valor).longValue();
            if (q >= 1 && q <= Integer.MAX_VALUE) {
                return (int) q;
            }
        }
        throw new IllegalArgumentException(ERRO_QUANTIDADE);
    }

    private ResultadoFerramenta erro(String mensagem) {
        String texto = (mensagem == null || mensagem.isBlank()) ? MENSAGEM_ERRO_PADRAO : mensagem;
        try {
            return new ResultadoFerramenta(mapper.writeValueAsString(Map.of("erro", texto)), true);
        } catch (RuntimeException e) {
            return new ResultadoFerramenta(JSON_ERRO_FIXO, true);
        }
    }

    private static Tool tool(String nome, String descricao, Tool.InputSchema.Properties props,
                             List<String> obrigatorios) {
        return Tool.builder()
                .name(nome)
                .description(descricao)
                .strict(true)
                .inputSchema(Tool.InputSchema.builder()
                        .properties(props)
                        .required(obrigatorios)
                        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                        .build())
                .build();
    }

    private static Tool.InputSchema.Properties propriedades(Object... paresNomeDefinicao) {
        var builder = Tool.InputSchema.Properties.builder();
        for (int i = 0; i < paresNomeDefinicao.length; i += 2) {
            builder.putAdditionalProperty((String) paresNomeDefinicao[i], (JsonValue) paresNomeDefinicao[i + 1]);
        }
        return builder.build();
    }

    private static JsonValue propriedade(String tipo, String descricao) {
        return JsonValue.from(Map.of("type", tipo, "description", descricao));
    }
}
