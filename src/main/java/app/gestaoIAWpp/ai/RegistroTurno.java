package app.gestaoIAWpp.ai;

import app.gestaoIAWpp.ai.dto.IteracaoTurnoResponse;
import app.gestaoIAWpp.ai.dto.ToolPedidaResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Registro das iterações de um turno. Um por turno; não é seguro para uso concorrente. */
public final class RegistroTurno {

    public static final String CHAVE = "registroTurno";

    private final List<IteracaoTurnoResponse> iteracoes = new ArrayList<>();

    public int iniciarIteracao() {
        int numero = iteracoes.size() + 1;
        iteracoes.add(new IteracaoTurnoResponse(numero, null, 0, 0, List.of(), null));
        return numero;
    }

    public void concluirIteracao(
            String finishReason, long tokensEntrada, long tokensSaida, List<ToolPedidaResponse> toolsPedidas) {
        IteracaoTurnoResponse atual = ultima();
        iteracoes.set(iteracoes.size() - 1, new IteracaoTurnoResponse(
                atual.numero(), finishReason, tokensEntrada, tokensSaida,
                List.copyOf(toolsPedidas), atual.observacao()));
    }

    public void observar(String texto) {
        if (iteracoes.isEmpty()) {
            return;
        }
        IteracaoTurnoResponse atual = ultima();
        iteracoes.set(iteracoes.size() - 1, new IteracaoTurnoResponse(
                atual.numero(), atual.finishReason(), atual.tokensEntrada(), atual.tokensSaida(),
                atual.toolsPedidas(), texto));
    }

    public int iteracoesIniciadas() {
        return iteracoes.size();
    }

    public List<IteracaoTurnoResponse> iteracoes() {
        return List.copyOf(iteracoes);
    }

    public static RegistroTurno de(Map<String, ?> contexto) {
        return (RegistroTurno) contexto.get(CHAVE);
    }

    private IteracaoTurnoResponse ultima() {
        if (iteracoes.isEmpty()) {
            throw new IllegalStateException("nenhuma iteração iniciada");
        }
        return iteracoes.get(iteracoes.size() - 1);
    }
}
