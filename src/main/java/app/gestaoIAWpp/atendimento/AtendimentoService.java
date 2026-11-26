package app.gestaoIAWpp.atendimento;

import app.gestaoIAWpp.ai.AgenteAtendimento;
import app.gestaoIAWpp.ai.AgenteResponse;
import app.gestaoIAWpp.cliente.ClienteResumo;
import app.gestaoIAWpp.cliente.ClienteService;
import app.gestaoIAWpp.empresa.NumeroWhatsappService;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Entrada agnóstica de canal: valida a mensagem, resolve número, empresa e cliente e repassa ao agente.
 * Fica entre o canal (simulador/WhatsApp) e a IA.
 */
@Service
public class AtendimentoService {

    private final NumeroWhatsappService numeros;
    private final ClienteService clientes;
    private final AgenteAtendimento agente;

    public AtendimentoService(NumeroWhatsappService numeros, ClienteService clientes, AgenteAtendimento agente) {
        this.numeros = numeros;
        this.clientes = clientes;
        this.agente = agente;
    }

    /** {@code telefone} pode ser nulo: o webhook do WhatsApp pode não informá-lo. */
    public AgenteResponse processarMensagem(String phoneNumberId, String userId, String telefone, String texto) {
        if (userId == null || userId.isBlank() || texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("Telefone e texto são obrigatórios.");
        }
        UUID empresaId = numeros.empresaDoNumero(phoneNumberId);
        ClienteResumo cliente = clientes.identificar(empresaId, userId, telefone);
        return agente.responder(empresaId, cliente.id(), texto.strip());
    }
}
