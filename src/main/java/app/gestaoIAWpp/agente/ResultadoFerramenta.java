package app.gestaoIAWpp.agente;

/** Resultado de uma tool: o JSON devolvido ao modelo e se ele representa um erro. */
public record ResultadoFerramenta(String conteudoJson, boolean erro) {
}
