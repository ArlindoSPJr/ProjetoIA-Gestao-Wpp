package app.gestaoIAWpp.ai;

import java.util.UUID;

/** Porta de entrada da IA. O resto do sistema não conhece o modelo nem o SDK por trás. */
public interface AgenteAtendimento {

    AgenteResponse responder(UUID empresaId, UUID clienteId, String mensagem);

    void reiniciarConversas();
}
