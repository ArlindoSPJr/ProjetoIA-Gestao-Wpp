package app.gestaoIAWpp.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;

class ToolRegistryTest {

    static class ToolA implements AgentTool {
        @Tool(name = "a", description = "tool a")
        public String a() {
            return "a";
        }
    }

    static class ToolBC implements AgentTool {
        @Tool(name = "b", description = "tool b")
        public String b() {
            return "b";
        }

        @Tool(name = "c", description = "tool c")
        public String c() {
            return "c";
        }
    }

    static class OutraToolA implements AgentTool {
        @Tool(name = "a", description = "outra tool a")
        public String a() {
            return "a2";
        }
    }

    private static List<String> nomes(ToolCallback[] callbacks) {
        return Arrays.stream(callbacks).map(c -> c.getToolDefinition().name()).toList();
    }

    @Test
    void juntaAsToolsDeTodasAsClasses() {
        ToolRegistry registry = new ToolRegistry(List.of(new ToolA(), new ToolBC()));

        assertThat(nomes(registry.callbacks())).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    void respeitaAOrdemDaLista() {
        ToolRegistry registry = new ToolRegistry(List.of(new ToolA(), new ToolBC()));

        List<String> nomes = nomes(registry.callbacks());

        assertThat(nomes.indexOf("a")).isLessThan(nomes.indexOf("b"));
        assertThat(nomes.indexOf("a")).isLessThan(nomes.indexOf("c"));
    }

    @Test
    void nomeDuplicadoEntreClassesFalha() {
        assertThatThrownBy(() -> new ToolRegistry(List.of(new ToolA(), new OutraToolA())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tool duplicada: a");
    }

    @Test
    void callbacksDevolveCopia() {
        ToolRegistry registry = new ToolRegistry(List.of(new ToolA(), new ToolBC()));

        ToolCallback[] primeira = registry.callbacks();
        primeira[0] = null;

        assertThat(registry.callbacks()).doesNotContainNull().hasSize(3);
    }
}
