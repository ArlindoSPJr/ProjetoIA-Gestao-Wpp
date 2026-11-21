package app.gestaoIAWpp.agente;

import java.util.List;

/**
 * Uma chamada à API do Claude dentro de um turno, para o simulador mostrar o loop passo a passo.
 *
 * @param numero      1 para a primeira chamada do turno, 2 para a seguinte, e assim por diante
 * @param envio       o que foi enviado à API
 * @param retorno     o que a API devolveu; {@code null} se a chamada falhou
 * @param ferramentas tools executadas a partir desta resposta (vazio se nenhuma foi executada)
 * @param observacao  aviso sobre o desfecho (limite atingido, falha, stop_reason inesperado); {@code null} se normal
 */
public record IteracaoAgente(int numero, EnvioApi envio, RetornoApi retorno, List<ChamadaFerramenta> ferramentas,
                             String observacao) {
}
