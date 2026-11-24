package app.gestaoIAWpp;

import static org.assertj.core.api.Assertions.assertThat;

import app.gestaoIAWpp.ai.ToolRegistry;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.ApplicationContext;

@SpringBootTest
class GestaoIaWppApplicationTests {

	@Autowired
	ApplicationContext contexto;

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

}
