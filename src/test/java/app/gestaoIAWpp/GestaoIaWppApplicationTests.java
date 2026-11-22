package app.gestaoIAWpp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

}
