package app.gestaoIAWpp;

import com.anthropic.client.AnthropicClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class GestaoIaWppApplicationTests {

	// Substitui o cliente real: o contexto sobe sem ANTHROPIC_API_KEY e nenhum teste chama a API.
	@MockitoBean
	AnthropicClient anthropicClient;

	@Test
	void contextLoads() {
	}

}
