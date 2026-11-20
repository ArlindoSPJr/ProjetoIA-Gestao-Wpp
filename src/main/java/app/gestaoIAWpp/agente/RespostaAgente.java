package app.gestaoIAWpp.agente;

import java.util.List;

public record RespostaAgente(String texto, List<ChamadaFerramenta> chamadas) {
}
