package app.gestaoIAWpp.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

/**
 * Junta as tools de todos os {@link AgentTool} na ordem recebida e falha cedo se dois nomes
 * coincidirem. O despacho pelo nome da tool é feito pelo Spring AI.
 */
public class ToolRegistry {

    private final ToolCallback[] callbacks;

    public ToolRegistry(List<AgentTool> tools) {
        List<ToolCallback> todos = new ArrayList<>();
        Set<String> nomes = new HashSet<>();
        for (AgentTool tool : tools) {
            for (ToolCallback callback : ToolCallbacks.from(tool)) {
                String nome = callback.getToolDefinition().name();
                if (!nomes.add(nome)) {
                    throw new IllegalStateException("Tool duplicada: " + nome);
                }
                todos.add(callback);
            }
        }
        this.callbacks = todos.toArray(new ToolCallback[0]);
    }

    public ToolCallback[] callbacks() {
        return callbacks.clone();
    }
}
