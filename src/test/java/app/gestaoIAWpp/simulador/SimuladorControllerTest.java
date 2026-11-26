package app.gestaoIAWpp.simulador;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.gestaoIAWpp.ai.AgenteAtendimento;
import app.gestaoIAWpp.ai.dto.IteracaoTurnoResponse;
import app.gestaoIAWpp.ai.dto.AgenteResponse;
import app.gestaoIAWpp.ai.dto.ToolPedidaResponse;
import app.gestaoIAWpp.atendimento.AtendimentoService;
import app.gestaoIAWpp.carrinho.CarrinhoResumo;
import app.gestaoIAWpp.carrinho.CarrinhoService;
import app.gestaoIAWpp.carrinho.ItemCarrinhoResumo;
import app.gestaoIAWpp.cliente.ClienteResumo;
import app.gestaoIAWpp.cliente.ClienteService;
import app.gestaoIAWpp.empresa.NumeroNaoCadastradoException;
import app.gestaoIAWpp.empresa.NumeroWhatsappService;
import app.gestaoIAWpp.produto.ProdutoService;
import app.gestaoIAWpp.produto.VarianteResumo;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = SimuladorController.class, properties = "simulador.phone-number-id=simulador-loja-demo")
class SimuladorControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AtendimentoService atendimento;
    @MockitoBean NumeroWhatsappService numeros;
    @MockitoBean AgenteAtendimento agente;
    @MockitoBean CarrinhoService carrinho;
    @MockitoBean ProdutoService produtos;
    @MockitoBean ClienteService clientes;

    @Test
    @DisplayName("T23 telefone formatado vira só dígitos e o user_id leva o prefixo BR.SIM")
    void postMensagemRetornaTextoEIteracoes() throws Exception {
        var iteracao = new IteracaoTurnoResponse(1, "tool_use", 10, 5,
                List.of(new ToolPedidaResponse("consultar_produto", "{}")), null);
        when(atendimento.processarMensagem("simulador-loja-demo", "BR.SIM5511999990000", "5511999990000", "oi"))
                .thenReturn(new AgenteResponse("olá", List.of(iteracao)));
        mvc.perform(post("/api/simulador/mensagens").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefone\":\"+55 (11) 99999-0000\",\"texto\":\"oi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value("olá"))
                .andExpect(jsonPath("$.iteracoes[0].finishReason").value("tool_use"))
                .andExpect(jsonPath("$.iteracoes[0].toolsPedidas[0].nome").value("consultar_produto"))
                .andExpect(jsonPath("$.chamadas").doesNotExist());
    }

    @Test
    @DisplayName("T23 telefone com dígitos fora de 10 a 15 retorna 400 sem chamar o atendimento")
    void deveRetornar400_quandoTelefoneInvalido() throws Exception {
        mvc.perform(post("/api/simulador/mensagens").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefone\":\"123\",\"texto\":\"oi\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro")
                        .value("Telefone inválido: use só números com DDI, ex.: 5511999990000."));
        verify(atendimento, never()).processarMensagem(anyString(), anyString(), any(), anyString());
    }

    @Test
    void deveRetornar400_quandoTelefoneEmBranco() throws Exception {
        when(atendimento.processarMensagem(anyString(), any(), any(), anyString()))
                .thenThrow(new IllegalArgumentException("Telefone e texto são obrigatórios."));
        mvc.perform(post("/api/simulador/mensagens").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefone\":\" \",\"texto\":\"oi\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Telefone e texto são obrigatórios."));
    }

    @Test
    @DisplayName("T22 número não cadastrado retorna 400 com a mensagem da exceção")
    void deveRetornar400_quandoNumeroNaoCadastrado() throws Exception {
        when(atendimento.processarMensagem(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new NumeroNaoCadastradoException());
        mvc.perform(post("/api/simulador/mensagens").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefone\":\"5511999990000\",\"texto\":\"oi\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Número de atendimento não cadastrado."));
    }

    @Test
    @DisplayName("T27 CA17 estado lista as variantes da empresa com o nome completo e o carrinho do cliente")
    void getEstadoRetornaCarrinhoEEstoque() throws Exception {
        UUID empresa = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        UUID varianteId = UUID.randomUUID();
        ClienteResumo cliente = new ClienteResumo(clienteId, "BR.SIM5511999990000", "5511999990000");
        when(numeros.empresaDoNumero("simulador-loja-demo")).thenReturn(empresa);
        when(clientes.buscar(empresa, "BR.SIM5511999990000")).thenReturn(Optional.of(cliente));
        var item = new ItemCarrinhoResumo(varianteId, "Camisa Básica (Preta / M)", new BigDecimal("49.90"), 2);
        when(carrinho.ver(clienteId)).thenReturn(new CarrinhoResumo(List.of(item), new BigDecimal("99.80")));
        when(produtos.listarVariantes(empresa)).thenReturn(List.of(new VarianteResumo(varianteId,
                "Camisa Básica (Preta / M)", "Cor: Preta, Tamanho: M", new BigDecimal("49.90"), true, 12)));
        mvc.perform(get("/api/simulador/estado").param("telefone", "5511999990000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carrinho.total").value(99.80))
                .andExpect(jsonPath("$.carrinho.itens[0].nome").value("Camisa Básica (Preta / M)"))
                .andExpect(jsonPath("$.carrinho.itens[0].precoUnitario").value(49.90))
                .andExpect(jsonPath("$.carrinho.itens[0].quantidade").value(2))
                .andExpect(jsonPath("$.carrinho.itens[0].subtotal").value(99.80))
                .andExpect(jsonPath("$.estoque.length()").value(1))
                .andExpect(jsonPath("$.estoque[0].varianteId").value(varianteId.toString()))
                .andExpect(jsonPath("$.estoque[0].nome").value("Camisa Básica (Preta / M)"))
                .andExpect(jsonPath("$.estoque[0].preco").value(49.90))
                .andExpect(jsonPath("$.estoque[0].quantidade").value(12));
    }

    @Test
    void getEstadoRetornaCarrinhoVazio_quandoClienteNaoExiste() throws Exception {
        UUID empresa = UUID.randomUUID();
        when(numeros.empresaDoNumero("simulador-loja-demo")).thenReturn(empresa);
        when(clientes.buscar(empresa, "BR.SIM5511999990000")).thenReturn(Optional.empty());
        mvc.perform(get("/api/simulador/estado").param("telefone", "5511999990000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carrinho.total").value(0))
                .andExpect(jsonPath("$.carrinho.itens.length()").value(0));
        verify(carrinho, never()).ver(any());
    }

    @Test
    void userIdDoSimulador_cobreLimitesEEmBranco() {
        assertNull(SimuladorController.userIdDoSimulador(null));
        assertNull(SimuladorController.userIdDoSimulador("  "));
        assertEquals("BR.SIM1234567890", SimuladorController.userIdDoSimulador("1234567890"));
        assertEquals("BR.SIM123456789012345", SimuladorController.userIdDoSimulador("123456789012345"));
        assertThrows(IllegalArgumentException.class, () -> SimuladorController.userIdDoSimulador("123456789"));
        assertThrows(IllegalArgumentException.class, () -> SimuladorController.userIdDoSimulador("1234567890123456"));
    }

    @Test
    @DisplayName("T27 reset limpa as conversas, reinicia os carrinhos da empresa da demo e devolve 204")
    void resetReiniciaConversasECarrinhosDaEmpresaERetorna204() throws Exception {
        UUID empresa = UUID.randomUUID();
        when(numeros.empresaDoNumero("simulador-loja-demo")).thenReturn(empresa);
        mvc.perform(post("/api/simulador/reset")).andExpect(status().isNoContent());
        verify(agente).reiniciarConversas();
        verify(carrinho).reiniciarDemo(empresa);
    }

    @Test
    void estadoSemTelefoneRetorna400() throws Exception {
        mvc.perform(get("/api/simulador/estado"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Requisição inválida."));
    }

    @Test
    @DisplayName("T27 estado com telefone em branco retorna 400 sem consultar a empresa")
    void deveRetornar400ComMensagemDoTelefone_quandoEstadoComTelefoneEmBranco() throws Exception {
        mvc.perform(get("/api/simulador/estado").param("telefone", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Telefone é obrigatório."));
        verify(numeros, never()).empresaDoNumero(anyString());
    }

    @Test
    void jsonMalformadoRetorna400() throws Exception {
        mvc.perform(post("/api/simulador/mensagens").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Requisição inválida."));
    }
}
