package app.gestaoIAWpp.agente;

/** Porta de entrada da IA. O resto do sistema não conhece o modelo nem o SDK por trás. */
public interface AgenteAtendimento {

    RespostaAgente responder(String telefone, String mensagem);

    void reiniciarConversas();
}
