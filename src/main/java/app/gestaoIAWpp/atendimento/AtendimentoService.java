package app.gestaoIAWpp.atendimento;

import app.gestaoIAWpp.agente.AgenteAtendimento;
import app.gestaoIAWpp.agente.RespostaAgente;
import org.springframework.stereotype.Service;

/** Valida a mensagem recebida e a repassa ao agente. Fica entre o canal (simulador/WhatsApp) e a IA. */
@Service
public class AtendimentoService {

    private final AgenteAtendimento agente;

    public AtendimentoService(AgenteAtendimento agente) {
        this.agente = agente;
    }

    public RespostaAgente processarMensagem(String telefone, String texto) {
        if (telefone == null || telefone.isBlank() || texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("Telefone e texto são obrigatórios.");
        }
        return agente.responder(telefone, texto.strip());
    }
}
