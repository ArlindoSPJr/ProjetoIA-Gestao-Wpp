package app.gestaoIAWpp.agente;

/** Uma tool pedida pelo modelo numa iteração: nome e entrada em JSON. */
public record ToolPedida(String nome, String entradaJson) {}
