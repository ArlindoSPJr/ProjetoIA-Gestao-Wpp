package app.gestaoIAWpp.atendimento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.gestaoIAWpp.ai.AgenteAtendimento;
import app.gestaoIAWpp.ai.dto.AgenteResponse;
import app.gestaoIAWpp.cliente.ClienteResumo;
import app.gestaoIAWpp.cliente.ClienteService;
import app.gestaoIAWpp.empresa.NumeroNaoCadastradoException;
import app.gestaoIAWpp.empresa.NumeroWhatsappService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AtendimentoServiceTest {

    private static final UUID EMPRESA = UUID.randomUUID();
    private static final UUID CLIENTE = UUID.randomUUID();
    private static final String NUMERO = "simulador-loja-demo";
    private static final String USER_ID = "BR.SIM5511999990000";

    private final NumeroWhatsappService numeros = mock(NumeroWhatsappService.class);
    private final ClienteService clientes = mock(ClienteService.class);
    private final AgenteAtendimento agente = mock(AgenteAtendimento.class);
    private final AtendimentoService service = new AtendimentoService(numeros, clientes, agente);

    private void numeroAtivo() {
        when(numeros.empresaDoNumero(NUMERO)).thenReturn(EMPRESA);
    }

    @Test
    @DisplayName("T22 recusa sem chamar o agente quando o número não existe ou está inativo")
    void deveRecusarSemChamarAgente_quandoNumeroNaoCadastradoOuInativo() {
        when(numeros.empresaDoNumero(NUMERO)).thenThrow(new NumeroNaoCadastradoException());

        assertThrows(NumeroNaoCadastradoException.class,
                () -> service.processarMensagem(NUMERO, USER_ID, "5511999990000", "oi"));

        verifyNoInteractions(agente, clientes);
    }

    @Test
    @DisplayName("T23 identifica o cliente e passa os ids ao agente")
    void deveIdentificarClienteEPassarIdsAoAgente_quandoNumeroValido() {
        numeroAtivo();
        ClienteResumo cliente = new ClienteResumo(CLIENTE, USER_ID, "5511999990000");
        when(clientes.identificar(EMPRESA, USER_ID, "5511999990000")).thenReturn(cliente);
        when(agente.responder(EMPRESA, CLIENTE, "oi")).thenReturn(new AgenteResponse("olá", List.of()));

        AgenteResponse r = service.processarMensagem(NUMERO, USER_ID, "5511999990000", "  oi ");

        assertThat(r.texto()).isEqualTo("olá");
        verify(agente).responder(EMPRESA, CLIENTE, "oi");
    }

    @Test
    void deveAceitarTelefoneNulo_quandoCanalNaoInformaTelefone() {
        numeroAtivo();
        ClienteResumo cliente = new ClienteResumo(CLIENTE, USER_ID, null);
        when(clientes.identificar(EMPRESA, USER_ID, null)).thenReturn(cliente);
        when(agente.responder(EMPRESA, CLIENTE, "oi")).thenReturn(new AgenteResponse("olá", List.of()));

        assertThat(service.processarMensagem(NUMERO, USER_ID, null, "oi").texto()).isEqualTo("olá");
    }

    @Test
    void deveLancarSemConsultarRepositorio_quandoUserIdOuTextoEmBranco() {
        var e1 = assertThrows(IllegalArgumentException.class,
                () -> service.processarMensagem(NUMERO, USER_ID, null, " "));
        assertThrows(IllegalArgumentException.class, () -> service.processarMensagem(NUMERO, null, null, "oi"));
        assertThrows(IllegalArgumentException.class, () -> service.processarMensagem(NUMERO, " ", null, "oi"));
        assertThrows(IllegalArgumentException.class, () -> service.processarMensagem(NUMERO, USER_ID, null, null));

        assertThat(e1).hasMessage("Telefone e texto são obrigatórios.");
        verifyNoInteractions(numeros, clientes, agente);
    }
}
