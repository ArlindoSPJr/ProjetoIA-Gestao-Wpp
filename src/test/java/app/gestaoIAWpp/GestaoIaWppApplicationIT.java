package app.gestaoIAWpp;

import static org.assertj.core.api.Assertions.assertThat;

import app.gestaoIAWpp.ai.ToolRegistry;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class GestaoIaWppApplicationIT {

	@Autowired
	ApplicationContext contexto;

	@Autowired
	JdbcTemplate jdbc;

	@Value("${simulador.phone-number-id}")
	String phoneNumberId;

	@Test
	void contextLoads() {
	}

	@Test
	void springAiConfigurado() {
		assertThat(contexto.getBeanProvider(ChatClient.Builder.class).getIfAvailable()).isNotNull();
		assertThat(contexto.getBean(ToolCallingManager.class)).isNotNull();
	}

	@Test
	void registryTemAsCincoToolsComProdutoAntesDeCarrinho() {
		List<String> nomes = Arrays.stream(contexto.getBean(ToolRegistry.class).callbacks())
				.map(ToolCallback::getToolDefinition).map(d -> d.name()).toList();
		assertThat(nomes).containsExactlyInAnyOrder("consultar_produto", "consultar_estoque",
				"adicionar_ao_carrinho", "remover_do_carrinho", "ver_carrinho");
		for (String produto : List.of("consultar_produto", "consultar_estoque")) {
			for (String carrinho : List.of("adicionar_ao_carrinho", "remover_do_carrinho", "ver_carrinho")) {
				assertThat(nomes.indexOf(produto)).isLessThan(nomes.indexOf(carrinho));
			}
		}
	}

	@Test
	@DisplayName("T01 schema e seed da Loja Demo aplicados na subida")
	void deveAplicarSchemaESeed_quandoAplicacaoSobe() {
		assertThat(jdbc.queryForObject("SELECT count(*) FROM empresa", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM numero_whatsapp WHERE ativo AND phone_number_id = ?", Integer.class,
				phoneNumberId)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM produto", Integer.class)).isEqualTo(6);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM variante", Integer.class)).isEqualTo(8);

		Map<String, Integer> estoque = new HashMap<>();
		jdbc.query("""
				SELECT p.nome || coalesce(' ' || string_agg(vo.valor, '/' ORDER BY o.posicao), '') AS chave,
				       v.quantidade_estoque AS qtd
				FROM variante v
				JOIN produto p ON p.id = v.produto_id
				LEFT JOIN variante_valor vv ON vv.variante_id = v.id
				LEFT JOIN valor_opcao vo ON vo.id = vv.valor_opcao_id
				LEFT JOIN opcao o ON o.id = vo.opcao_id
				GROUP BY v.id, p.nome, v.quantidade_estoque
				""", rs -> {
			estoque.put(rs.getString("chave"), rs.getInt("qtd"));
		});
		assertThat(estoque).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
				Map.entry("Camisa Básica Preta/M", 12),
				Map.entry("Camisa Básica Preta/G", 0),
				Map.entry("Camisa Básica Branca/M", 8),
				Map.entry("Camisa Polo Azul/M", 5),
				Map.entry("Calça Jeans Masculina 42", 3),
				Map.entry("Calça Jeans Feminina 38", 6),
				Map.entry("Tênis Casual Branco/40", 4),
				Map.entry("Boné Ajustável", 15)));
	}

}
