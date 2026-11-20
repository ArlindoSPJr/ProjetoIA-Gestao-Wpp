package app.gestaoIAWpp.agente;

/** Registro de uma tool executada num turno, para exibição no simulador. */
public record ChamadaFerramenta(String nome, String entradaJson, String resultadoJson, boolean erro) {
}
