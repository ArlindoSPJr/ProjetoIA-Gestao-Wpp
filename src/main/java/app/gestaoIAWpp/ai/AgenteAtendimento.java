package app.gestaoIAWpp.ai;

import app.gestaoIAWpp.ai.dto.AgenteResponse;

/** Porta de entrada da IA. O resto do sistema não conhece o modelo nem o SDK por trás. */
public interface AgenteAtendimento {

    AgenteResponse responder(String telefone, String mensagem);

    void reiniciarConversas();
}
