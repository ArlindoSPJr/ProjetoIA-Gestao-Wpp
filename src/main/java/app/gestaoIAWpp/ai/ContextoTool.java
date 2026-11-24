package app.gestaoIAWpp.ai;

import org.springframework.ai.chat.model.ToolContext;

/**
 * Acesso ao {@link ToolContext} das tools: o telefone vem sempre do código, nunca do modelo.
 */
public final class ContextoTool {

    public static final String CHAVE_TELEFONE = "telefone";

    private ContextoTool() {
    }

    public static String telefone(ToolContext ctx) {
        Object telefone = ctx == null ? null : ctx.getContext().get(CHAVE_TELEFONE);
        if (telefone == null) {
            throw new IllegalStateException("Telefone ausente no contexto da ferramenta.");
        }
        return telefone.toString();
    }
}
