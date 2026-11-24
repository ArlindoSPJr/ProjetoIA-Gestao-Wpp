package app.gestaoIAWpp.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

class ContextoToolTest {

    private static final UUID EMPRESA = UUID.randomUUID();
    private static final UUID CLIENTE = UUID.randomUUID();

    @Test
    @DisplayName("T24 devolve empresaId e clienteId do contexto")
    void deveDevolverOsIds_quandoContextoTemAsDuasChaves() {
        ToolContext ctx = new ToolContext(Map.of(
                ContextoTool.CHAVE_EMPRESA_ID, EMPRESA, ContextoTool.CHAVE_CLIENTE_ID, CLIENTE));

        assertThat(ContextoTool.empresaId(ctx)).isEqualTo(EMPRESA);
        assertThat(ContextoTool.clienteId(ctx)).isEqualTo(CLIENTE);
    }

    @Test
    @DisplayName("T24 falha sem empresaId")
    void deveFalhar_quandoEmpresaAusente() {
        ToolContext ctx = new ToolContext(Map.of(ContextoTool.CHAVE_CLIENTE_ID, CLIENTE));

        assertThatThrownBy(() -> ContextoTool.empresaId(ctx))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Empresa ausente no contexto da ferramenta.");
    }

    @Test
    @DisplayName("T24 falha sem clienteId")
    void deveFalhar_quandoClienteAusente() {
        ToolContext ctx = new ToolContext(Map.of(ContextoTool.CHAVE_EMPRESA_ID, EMPRESA));

        assertThatThrownBy(() -> ContextoTool.clienteId(ctx))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cliente ausente no contexto da ferramenta.");
    }

    @Test
    void deveFalhar_quandoContextoNulo() {
        assertThatThrownBy(() -> ContextoTool.clienteId(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cliente ausente no contexto da ferramenta.");
        assertThatThrownBy(() -> ContextoTool.empresaId(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Empresa ausente no contexto da ferramenta.");
    }
}
