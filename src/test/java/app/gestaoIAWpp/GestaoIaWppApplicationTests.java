package app.gestaoIAWpp;

import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.client.AnthropicClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class GestaoIaWppApplicationTests {

	// Substitui o cliente real: o contexto sobe sem ANTHROPIC_API_KEY e nenhum teste chama a API.
	@MockitoBean
	AnthropicClient anthropicClient;

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

}
