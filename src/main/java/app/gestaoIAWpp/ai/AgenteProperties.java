package app.gestaoIAWpp.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("agente")
public record AgenteProperties(
        @DefaultValue("claude-haiku-5-5") String modelo,
        @DefaultValue("low") String effort,
        @DefaultValue("5") int maxIteracoes) {
}
