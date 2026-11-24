package app.gestaoIAWpp.ai;

import static app.gestaoIAWpp.ai.ContextoTool.CHAVE_TELEFONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.gestaoIAWpp.carrinho.CarrinhoService;
import app.gestaoIAWpp.carrinho.CarrinhoTool;
import app.gestaoIAWpp.produto.EstoqueService;
import app.gestaoIAWpp.produto.ProdutoRepositoryEmMemoria;
import app.gestaoIAWpp.produto.ProdutoService;
import app.gestaoIAWpp.produto.ProdutoTool;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.ToolExecutionException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Caracterização: congela nomes, descrições, schemas de entrada e saídas JSON das 5 tools
 * tal como estão hoje. Estes testes não devem mudar durante a reorganização das tools.
 */
class CaracterizacaoToolsTest {

    private static final String TEL = "5511999990000";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final String SAIDA_CARRINHO_P001_X2 =
            "{\"itens\":[{\"produtoId\":\"P001\",\"nome\":\"Camisa Básica Preta M\",\"precoUnitario\":49.90,"
            + "\"quantidade\":2,\"subtotal\":99.80}],\"total\":99.80}";

    private EstoqueService estoque;
    private ToolContext ctx;
    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        var produtos = new ProdutoRepositoryEmMemoria();
        estoque = new EstoqueService();
        var carrinho = new CarrinhoService(produtos, estoque);
        registry = new ToolRegistry(List.of(new ProdutoTool(new ProdutoService(produtos, estoque)), new CarrinhoTool(carrinho)));
        ctx = new ToolContext(Map.of(CHAVE_TELEFONE, TEL));
    }

    private ToolCallback[] callbacks() {
        return registry.callbacks();
    }

    private ToolCallback callback(String nome) {
        for (ToolCallback c : callbacks()) {
            if (c.getToolDefinition().name().equals(nome)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Tool não encontrada: " + nome);
    }

    private static JsonNode schemaEsperado(String nome) throws IOException {
        try (InputStream in = CaracterizacaoToolsTest.class
                .getResourceAsStream("/caracterizacao-tools/" + nome + ".json")) {
            assertThat(in).as("recurso de schema de %s", nome).isNotNull();
            return MAPPER.readTree(in);
        }
    }

    @Test
    void nomesDasTools() {
        assertThat(Arrays.stream(callbacks()).map(c -> c.getToolDefinition().name()))
                .containsExactlyInAnyOrder("consultar_produto", "consultar_estoque", "adicionar_ao_carrinho",
                        "remover_do_carrinho", "ver_carrinho");
    }

    @Test
    void descricoes() {
        assertThat(callback("consultar_produto").getToolDefinition().description()).isEqualTo(
                "Busca produtos da loja a partir do que o cliente pediu. Use sempre que o cliente perguntar "
                + "por um produto, preço ou disponibilidade, e antes de adicionar algo ao carrinho "
                + "quando ainda não tiver o id. Faça uma busca por tipo de produto: se o cliente pedir "
                + "vários tipos (ex.: camisa e tênis), chame esta ferramenta uma vez para cada tipo. "
                + "Retorna os 5 produtos mais relevantes com id, preço e estoque. Se o resultado "
                + "indicar que há mais produtos, mostre as opções ao cliente e peça mais detalhes "
                + "(modelo, cor, tamanho) para refinar a busca.");
        assertThat(callback("consultar_estoque").getToolDefinition().description()).isEqualTo(
                "Consulta a quantidade disponível em estoque de um produto já identificado. Use quando o "
                + "cliente perguntar se ainda há unidades de um produto encontrado antes.");
        assertThat(callback("adicionar_ao_carrinho").getToolDefinition().description()).isEqualTo(
                "Adiciona um produto ao carrinho do cliente e reserva o estoque. Use quando o cliente já "
                + "tiver dito claramente qual produto e quantas unidades quer; se houver mais de uma "
                + "opção possível ou a quantidade não estiver clara, pergunte antes. Use o id "
                + "retornado por consultar_produto; nunca invente ids.");
        assertThat(callback("remover_do_carrinho").getToolDefinition().description()).isEqualTo(
                "Remove um produto do carrinho e devolve as unidades ao estoque. Sem quantidade, remove "
                + "todas as unidades daquele produto.");
        assertThat(callback("ver_carrinho").getToolDefinition().description())
                .isEqualTo("Mostra os itens do carrinho do cliente e o total.");
    }

    @Test
    void schemas() throws IOException {
        for (String nome : new String[] {"consultar_produto", "consultar_estoque", "adicionar_ao_carrinho",
                "remover_do_carrinho", "ver_carrinho"}) {
            JsonNode atual = MAPPER.readTree(callback(nome).getToolDefinition().inputSchema());
            assertThat(atual).as("schema de %s", nome).isEqualTo(schemaEsperado(nome));
        }
    }

    @Test
    void consultarProdutoCamisaPreta() {
        String saida = callback("consultar_produto").call("{\"termo\":\"camisa preta\"}", ctx);
        assertThat(saida).isEqualTo(
                "{\"produtos\":[{\"id\":\"P001\",\"nome\":\"Camisa Básica Preta M\",\"descricao\":\"Camisa de algodão, gola redonda\","
                + "\"preco\":49.90,\"emEstoque\":true,\"quantidadeDisponivel\":12},"
                + "{\"id\":\"P002\",\"nome\":\"Camisa Básica Preta G\",\"descricao\":\"Camisa de algodão, gola redonda\","
                + "\"preco\":49.90,\"emEstoque\":false,\"quantidadeDisponivel\":0},"
                + "{\"id\":\"P003\",\"nome\":\"Camisa Básica Branca M\",\"descricao\":\"Camisa de algodão, gola redonda\","
                + "\"preco\":49.90,\"emEstoque\":true,\"quantidadeDisponivel\":8},"
                + "{\"id\":\"P004\",\"nome\":\"Camisa Polo Azul M\",\"descricao\":\"Camisa polo de piquet\","
                + "\"preco\":79.90,\"emEstoque\":true,\"quantidadeDisponivel\":5}],\"totalEncontrado\":4}");
    }

    @Test
    void consultarProdutoSemResultado() {
        String saida = callback("consultar_produto").call("{\"termo\":\"geladeira\"}", ctx);
        assertThat(saida).isEqualTo(
                "{\"produtos\":[],\"totalEncontrado\":0,\"mensagem\":\"Nenhum produto encontrado para 'geladeira'.\"}");
    }

    @Test
    void consultarEstoque() {
        String saida = callback("consultar_estoque").call("{\"produtoId\":\"P001\"}", ctx);
        assertThat(saida).isEqualTo(
                "{\"produtoId\":\"P001\",\"nome\":\"Camisa Básica Preta M\",\"emEstoque\":true,\"quantidadeDisponivel\":12}");
    }

    @Test
    void adicionarAoCarrinho() {
        String saida = callback("adicionar_ao_carrinho").call("{\"produtoId\":\"P001\",\"quantidade\":2}", ctx);
        assertThat(saida).isEqualTo(SAIDA_CARRINHO_P001_X2);
    }

    @Test
    void removerDoCarrinho() {
        callback("adicionar_ao_carrinho").call("{\"produtoId\":\"P001\",\"quantidade\":2}", ctx);
        String saida = callback("remover_do_carrinho").call("{\"produtoId\":\"P001\",\"quantidade\":1}", ctx);
        assertThat(saida).isEqualTo(
                "{\"itens\":[{\"produtoId\":\"P001\",\"nome\":\"Camisa Básica Preta M\",\"precoUnitario\":49.90,"
                + "\"quantidade\":1,\"subtotal\":49.90}],\"total\":49.90}");
    }

    @Test
    void verCarrinho() {
        callback("adicionar_ao_carrinho").call("{\"produtoId\":\"P001\",\"quantidade\":2}", ctx);
        String saida = callback("ver_carrinho").call("{}", ctx);
        assertThat(saida).isEqualTo(SAIDA_CARRINHO_P001_X2);
    }

    @Test
    void produtoInexistente() {
        assertThatThrownBy(() -> callback("consultar_estoque").call("{\"produtoId\":\"P999\"}", ctx))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessage("Produto P999 não encontrado.");
    }

    @Test
    void removerItemForaDoCarrinho() {
        assertThatThrownBy(() -> callback("remover_do_carrinho").call("{\"produtoId\":\"P002\"}", ctx))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessage("Produto P002 não está no carrinho.");
    }
}
