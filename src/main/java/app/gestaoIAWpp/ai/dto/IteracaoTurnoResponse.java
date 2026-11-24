package app.gestaoIAWpp.ai.dto;

import java.util.List;

/** Uma chamada à API dentro de um turno. {@code finishReason} é null se a iteração não chegou a concluir. */
public record IteracaoTurnoResponse(
        int numero,
        String finishReason,
        long tokensEntrada,
        long tokensSaida,
        List<ToolPedidaResponse> toolsPedidas,
        String observacao) {}
