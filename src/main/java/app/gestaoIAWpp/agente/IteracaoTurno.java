package app.gestaoIAWpp.agente;

import java.util.List;

/** Uma chamada à API dentro de um turno. {@code finishReason} é null se a iteração não chegou a concluir. */
public record IteracaoTurno(
        int numero,
        String finishReason,
        long tokensEntrada,
        long tokensSaida,
        List<ToolPedida> toolsPedidas,
        String observacao) {}
