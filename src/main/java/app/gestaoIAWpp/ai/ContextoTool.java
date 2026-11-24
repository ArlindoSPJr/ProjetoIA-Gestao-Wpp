package app.gestaoIAWpp.ai;

import java.util.UUID;
import org.springframework.ai.chat.model.ToolContext;

/**
 * Acesso ao {@link ToolContext} das tools: empresa e cliente vêm sempre do código, nunca do modelo.
 */
public final class ContextoTool {

    public static final String CHAVE_EMPRESA_ID = "empresaId";
    public static final String CHAVE_CLIENTE_ID = "clienteId";

    private ContextoTool() {
    }

    public static UUID empresaId(ToolContext ctx) {
        return obter(ctx, CHAVE_EMPRESA_ID, "Empresa ausente no contexto da ferramenta.");
    }

    public static UUID clienteId(ToolContext ctx) {
        return obter(ctx, CHAVE_CLIENTE_ID, "Cliente ausente no contexto da ferramenta.");
    }

    private static UUID obter(ToolContext ctx, String chave, String mensagem) {
        Object valor = ctx == null ? null : ctx.getContext().get(chave);
        if (valor == null) {
            throw new IllegalStateException(mensagem);
        }
        return (UUID) valor;
    }
}
