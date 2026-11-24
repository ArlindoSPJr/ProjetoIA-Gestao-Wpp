package app.gestaoIAWpp.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

class ContextoToolTest {

    @Test
    void devolveOTelefoneDoContexto() {
        ToolContext ctx = new ToolContext(Map.of(ContextoTool.CHAVE_TELEFONE, "5511999990000"));

        assertThat(ContextoTool.telefone(ctx)).isEqualTo("5511999990000");
    }

    @Test
    void semAChaveFalha() {
        ToolContext ctx = new ToolContext(Map.of());

        assertThatThrownBy(() -> ContextoTool.telefone(ctx))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Telefone ausente no contexto da ferramenta.");
    }

    @Test
    void contextoNuloFalha() {
        assertThatThrownBy(() -> ContextoTool.telefone(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Telefone ausente no contexto da ferramenta.");
    }
}
