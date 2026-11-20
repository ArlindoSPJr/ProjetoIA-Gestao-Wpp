package app.gestaoIAWpp.atendimento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.gestaoIAWpp.agente.AgenteAtendimento;
import app.gestaoIAWpp.agente.RespostaAgente;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AtendimentoServiceTest {

    private final AgenteAtendimento agente = Mockito.mock(AgenteAtendimento.class);
    private final AtendimentoService service = new AtendimentoService(agente);

    @Test
    void delegaAoAgenteComTextoSemEspacos() {
        when(agente.responder("5511", "oi")).thenReturn(new RespostaAgente("olá", List.of()));
        assertThat(service.processarMensagem("5511", "  oi ").texto()).isEqualTo("olá");
    }

    @Test
    void textoOuTelefoneEmBrancoLanca() {
        assertThrows(IllegalArgumentException.class, () -> service.processarMensagem("5511", " "));
        assertThrows(IllegalArgumentException.class, () -> service.processarMensagem(null, "oi"));
        verifyNoInteractions(agente);
    }
}
