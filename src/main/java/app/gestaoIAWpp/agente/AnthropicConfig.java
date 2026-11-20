package app.gestaoIAWpp.agente;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnthropicConfig {

    /**
     * A chave vem do Spring: de uma variável de ambiente ANTHROPIC_API_KEY ou do arquivo .env
     * (importado em application.yaml). O SDK sozinho só enxerga variáveis de ambiente reais,
     * por isso a chave é repassada explicitamente quando presente.
     */
    @Bean
    public AnthropicClient anthropicClient(@Value("${ANTHROPIC_API_KEY:}") String apiKey) {
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder().fromEnv();
        if (!apiKey.isBlank()) {
            builder.apiKey(apiKey);
        }
        return builder.build();
    }
}
