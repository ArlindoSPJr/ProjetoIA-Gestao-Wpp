package app.gestaoIAWpp.cliente;

import java.util.UUID;

/** Visão do cliente que sai do pacote {@code cliente}; a entidade {@link Cliente} não vaza. */
public record ClienteResumo(UUID id, String userId, String telefone) {
}
