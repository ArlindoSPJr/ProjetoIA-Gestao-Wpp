package app.gestaoIAWpp.agente;

/**
 * Resumo de uma resposta da Messages API.
 *
 * @param conteudoJson os blocos de conteúdo devolvidos (text, tool_use...) em JSON
 */
public record RetornoApi(String stopReason, long tokensEntrada, long tokensSaida, String conteudoJson) {
}
