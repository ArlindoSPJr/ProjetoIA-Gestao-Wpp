package app.gestaoIAWpp.agente;

import java.util.List;

/**
 * @param texto     a resposta que vai ao cliente
 * @param iteracoes cada chamada à API do turno, com finish reason, tokens e tools pedidas (para o simulador)
 */
public record RespostaAgente(String texto, List<IteracaoTurno> iteracoes) {
}
