package app.gestaoIAWpp.agente;

/**
 * Resumo de uma requisição à Messages API.
 *
 * @param totalMensagens     mensagens enviadas (o histórico inteiro vai em toda requisição)
 * @param novasMensagensJson só as mensagens que entraram desde a requisição anterior (JSON)
 * @param requisicaoJson     a requisição completa, como o SDK a envia (JSON)
 */
public record EnvioApi(String modelo, String effort, long maxTokens, int totalMensagens, int totalTools,
                       String novasMensagensJson, String requisicaoJson) {
}
