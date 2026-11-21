package app.gestaoIAWpp.agente;

import java.util.List;

/**
 * @param chamadas  todas as tools executadas no turno, em ordem
 * @param iteracoes cada chamada à API do turno, com envio, retorno e tools (para o simulador)
 */
public record RespostaAgente(String texto, List<ChamadaFerramenta> chamadas, List<IteracaoAgente> iteracoes) {
}
