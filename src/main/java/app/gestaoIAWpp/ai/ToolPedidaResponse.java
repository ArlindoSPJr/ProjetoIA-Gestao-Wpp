package app.gestaoIAWpp.ai;

/** Uma tool pedida pelo modelo numa iteração: nome e entrada em JSON. */
public record ToolPedidaResponse(String nome, String entradaJson) {}
