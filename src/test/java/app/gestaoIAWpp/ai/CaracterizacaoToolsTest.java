package app.gestaoIAWpp.ai;

import static app.gestaoIAWpp.ai.ContextoTool.CHAVE_CLIENTE_ID;
import static app.gestaoIAWpp.ai.ContextoTool.CHAVE_EMPRESA_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.gestaoIAWpp.carrinho.CarrinhoTeste;
import app.gestaoIAWpp.carrinho.CarrinhoTool;
import app.gestaoIAWpp.produto.CatalogoTeste;
import app.gestaoIAWpp.produto.ProdutoTool;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.ToolExecutionException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Caracterização: congela nomes, descrições, schemas de entrada e saídas JSON das 5 tools.
 * Mudança aqui é mudança do contrato com o modelo.
 */
class CaracterizacaoToolsTest {

    private static final UUID EMPRESA = UUID.randomUUID();
    private static final UUID CLIENTE = UUID.randomUUID();
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private CatalogoTeste catalogo;
    private String pretaM;
    private ToolContext ctx;
    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        catalogo = CatalogoTeste.lojaDemo(EMPRESA);
        var produtoService = catalogo.produtoService();
        var carrinho = CarrinhoTeste.servico(produtoService);
        registry = new ToolRegistry(List.of(new ProdutoTool(produtoService), new CarrinhoTool(carrinho)));
        ctx = new ToolContext(Map.of(CHAVE_EMPRESA_ID, EMPRESA, CHAVE_CLIENTE_ID, CLIENTE));
        pretaM = id("Camisa Básica (Preta / M)");
    }

    private String id(String nomeCompleto) {
        return catalogo.variante(nomeCompleto).getId().toString();
    }

    private String carrinhoPretaM(int quantidade, String subtotal) {
        return "{\"itens\":[{\"varianteId\":\"" + pretaM + "\",\"nome\":\"Camisa Básica (Preta / M)\","
                + "\"precoUnitario\":49.90,\"quantidade\":" + quantidade + ",\"subtotal\":" + subtotal + "}],"
                + "\"total\":" + subtotal + "}";
    }

    private ToolCallback callback(String nome) {
        return Arrays.stream(registry.callbacks())
                .filter(c -> c.getToolDefinition().name().equals(nome))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tool não encontrada: " + nome));
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
        assertThat(Arrays.stream(registry.callbacks()).map(c -> c.getToolDefinition().name()))
                .containsExactlyInAnyOrder("consultar_produto", "consultar_estoque", "adicionar_ao_carrinho",
                        "remover_do_carrinho", "ver_carrinho");
    }

    @Test
    void descricoes() {
        assertThat(callback("consultar_produto").getToolDefinition().description()).isEqualTo(
                "Busca produtos da loja a partir do que o cliente pediu. Use sempre que o cliente perguntar "
                + "por um produto, preço ou disponibilidade, e antes de adicionar algo ao carrinho "
                + "quando ainda não tiver o id da variante. Faça uma busca por tipo de produto: se o cliente "
                + "pedir vários tipos (ex.: camisa e tênis), chame esta ferramenta uma vez para cada tipo. "
                + "Retorna os 5 produtos mais relevantes, cada um com todas as suas variantes (opções como "
                + "cor e tamanho, id da variante, preço e estoque). Se o resultado indicar que há mais "
                + "produtos, mostre as opções ao cliente e peça mais detalhes (modelo, cor, tamanho) para "
                + "refinar a busca.");
        assertThat(callback("consultar_estoque").getToolDefinition().description()).isEqualTo(
                "Consulta a quantidade disponível em estoque de uma variante já identificada. Use quando o "
                + "cliente perguntar se ainda há unidades de uma variante encontrada antes.");
        assertThat(callback("adicionar_ao_carrinho").getToolDefinition().description()).isEqualTo(
                "Adiciona uma variante de produto ao carrinho do cliente e reserva o estoque. Use quando o "
                + "cliente já tiver dito claramente qual produto, qual variante (cor, tamanho) e quantas "
                + "unidades quer; se houver mais de uma opção possível ou a quantidade não estiver clara, "
                + "pergunte antes. Use o id da variante retornado por consultar_produto; nunca invente ids.");
        assertThat(callback("remover_do_carrinho").getToolDefinition().description()).isEqualTo(
                "Remove uma variante do carrinho e devolve as unidades ao estoque. Sem quantidade, remove "
                + "todas as unidades daquela variante.");
        assertThat(callback("ver_carrinho").getToolDefinition().description())
                .isEqualTo("Mostra os itens do carrinho do cliente e o total.");
    }

    @Test
    @DisplayName("T25 schemas das 5 tools, com varianteId nas tools de ação")
    void schemas() throws IOException {
        for (String nome : new String[] {"consultar_produto", "consultar_estoque", "adicionar_ao_carrinho",
                "remover_do_carrinho", "ver_carrinho"}) {
            JsonNode atual = MAPPER.readTree(callback(nome).getToolDefinition().inputSchema());
            assertThat(atual).as("schema de %s", nome).isEqualTo(schemaEsperado(nome));
        }
    }

    @Test
    @DisplayName("D5 consultar_produto devolve produtos com a lista de variantes")
    void consultarProdutoCamisaPreta() {
        String basica = catalogo.produtos().get(0).getId().toString();
        String polo = catalogo.produtos().get(1).getId().toString();

        String saida = callback("consultar_produto").call("{\"termo\":\"camisa preta\"}", ctx);

        assertThat(saida).isEqualTo(
                "{\"produtos\":[{\"produtoId\":\"" + basica + "\",\"nome\":\"Camisa Básica\","
                + "\"descricao\":\"Camisa de algodão, gola redonda\",\"variantes\":["
                + "{\"varianteId\":\"" + pretaM + "\",\"opcoes\":\"Cor: Preta, Tamanho: M\",\"preco\":49.90,"
                + "\"emEstoque\":true,\"quantidadeDisponivel\":12},"
                + "{\"varianteId\":\"" + id("Camisa Básica (Preta / G)") + "\",\"opcoes\":\"Cor: Preta, Tamanho: G\","
                + "\"preco\":49.90,\"emEstoque\":false,\"quantidadeDisponivel\":0},"
                + "{\"varianteId\":\"" + id("Camisa Básica (Branca / M)") + "\",\"opcoes\":\"Cor: Branca, Tamanho: M\","
                + "\"preco\":49.90,\"emEstoque\":true,\"quantidadeDisponivel\":8}]},"
                + "{\"produtoId\":\"" + polo + "\",\"nome\":\"Camisa Polo\",\"descricao\":\"Camisa polo de piquet\","
                + "\"variantes\":[{\"varianteId\":\"" + id("Camisa Polo (Azul / M)") + "\","
                + "\"opcoes\":\"Cor: Azul, Tamanho: M\",\"preco\":79.90,\"emEstoque\":true,"
                + "\"quantidadeDisponivel\":5}]}],\"totalEncontrado\":2}");
    }

    @Test
    void consultarProdutoSemOpcoesOmiteOCampo() {
        String saida = callback("consultar_produto").call("{\"termo\":\"bone\"}", ctx);

        assertThat(saida).contains("\"nome\":\"Boné Ajustável\"").doesNotContain("\"opcoes\"");
    }

    @Test
    void consultarProdutoSemResultado() {
        String saida = callback("consultar_produto").call("{\"termo\":\"geladeira\"}", ctx);
        assertThat(saida).isEqualTo(
                "{\"produtos\":[],\"totalEncontrado\":0,\"mensagem\":\"Nenhum produto encontrado para 'geladeira'.\"}");
    }

    @Test
    void consultarEstoque() {
        String saida = callback("consultar_estoque").call("{\"varianteId\":\"" + pretaM + "\"}", ctx);
        assertThat(saida).isEqualTo("{\"varianteId\":\"" + pretaM + "\",\"nome\":\"Camisa Básica (Preta / M)\","
                + "\"opcoes\":\"Cor: Preta, Tamanho: M\",\"emEstoque\":true,\"quantidadeDisponivel\":12}");
    }

    @Test
    void consultarEstoqueSemOpcoesOmiteOCampo() {
        String bone = id("Boné Ajustável");
        String saida = callback("consultar_estoque").call("{\"varianteId\":\"" + bone + "\"}", ctx);
        assertThat(saida).isEqualTo("{\"varianteId\":\"" + bone + "\",\"nome\":\"Boné Ajustável\","
                + "\"emEstoque\":true,\"quantidadeDisponivel\":15}");
    }

    @Test
    void adicionarAoCarrinho() {
        String saida = callback("adicionar_ao_carrinho")
                .call("{\"varianteId\":\"" + pretaM + "\",\"quantidade\":2}", ctx);
        assertThat(saida).isEqualTo(carrinhoPretaM(2, "99.80"));
    }

    @Test
    void removerDoCarrinho() {
        callback("adicionar_ao_carrinho").call("{\"varianteId\":\"" + pretaM + "\",\"quantidade\":2}", ctx);
        String saida = callback("remover_do_carrinho")
                .call("{\"varianteId\":\"" + pretaM + "\",\"quantidade\":1}", ctx);
        assertThat(saida).isEqualTo(carrinhoPretaM(1, "49.90"));
    }

    @Test
    void verCarrinho() {
        callback("adicionar_ao_carrinho").call("{\"varianteId\":\"" + pretaM + "\",\"quantidade\":2}", ctx);
        String saida = callback("ver_carrinho").call("{}", ctx);
        assertThat(saida).isEqualTo(carrinhoPretaM(2, "99.80"));
    }

    @Test
    @DisplayName("Review Focus: varianteId que não é UUID responde 'Variante <id> não encontrada.'")
    void deveResponderVarianteNaoEncontrada_quandoIdNaoEhUuid() {
        assertThatThrownBy(() -> callback("consultar_estoque").call("{\"varianteId\":\"P001\"}", ctx))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessage("Variante P001 não encontrada.");
        assertThatThrownBy(() -> callback("adicionar_ao_carrinho").call("{\"varianteId\":\"\",\"quantidade\":1}", ctx))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessage("Variante  não encontrada.");
    }

    @Test
    void deveResponderVarianteNaoEncontrada_quandoUuidNaoEhDaEmpresa() {
        String outra = UUID.randomUUID().toString();
        assertThatThrownBy(() -> callback("adicionar_ao_carrinho")
                .call("{\"varianteId\":\"" + outra + "\",\"quantidade\":1}", ctx))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessage("Variante " + outra + " não encontrada.");
    }

    @Test
    void removerItemForaDoCarrinho() {
        String pretaG = id("Camisa Básica (Preta / G)");
        assertThatThrownBy(() -> callback("remover_do_carrinho").call("{\"varianteId\":\"" + pretaG + "\"}", ctx))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessage("Variante " + pretaG + " não está no carrinho.");
    }

    @Test
    void estoqueInsuficienteUsaONomeDaVariante() {
        assertThatThrownBy(() -> callback("adicionar_ao_carrinho")
                .call("{\"varianteId\":\"" + pretaM + "\",\"quantidade\":13}", ctx))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessage("Estoque insuficiente para Camisa Básica (Preta / M): solicitado 13, disponível 12.");
    }
}
