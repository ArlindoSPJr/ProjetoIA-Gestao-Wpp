package app.gestaoIAWpp.simulador;

import static org.mockito.ArgumentMatchers.anyString;
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
import app.gestaoIAWpp.carrinho.ItemCarrinho;
import app.gestaoIAWpp.produto.EstoqueService;
import app.gestaoIAWpp.produto.ProdutoRepositoryEmMemoria;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SimuladorController.class)
@Import(ProdutoRepositoryEmMemoria.class)
class SimuladorControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AtendimentoService atendimento;
    @MockitoBean AgenteAtendimento agente;
    @MockitoBean CarrinhoService carrinho;
    @MockitoBean EstoqueService estoque;

    @Test
    void postMensagemRetornaTextoEIteracoes() throws Exception {
        var iteracao = new IteracaoTurnoResponse(1, "tool_use", 10, 5,
                List.of(new ToolPedidaResponse("consultar_produto", "{}")), null);
        when(atendimento.processarMensagem("5511", "oi"))
                .thenReturn(new AgenteResponse("olá", List.of(iteracao)));
        mvc.perform(post("/api/simulador/mensagens").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefone\":\"5511\",\"texto\":\"oi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value("olá"))
                .andExpect(jsonPath("$.iteracoes[0].finishReason").value("tool_use"))
                .andExpect(jsonPath("$.iteracoes[0].toolsPedidas[0].nome").value("consultar_produto"))
                .andExpect(jsonPath("$.chamadas").doesNotExist());
    }

    @Test
    void postMensagemInvalidaRetorna400() throws Exception {
        when(atendimento.processarMensagem(anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("Telefone e texto são obrigatórios."));
        mvc.perform(post("/api/simulador/mensagens").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefone\":\"5511\",\"texto\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Telefone e texto são obrigatórios."));
    }

    @Test
    void getEstadoRetornaCarrinhoEEstoque() throws Exception {
        var item = new ItemCarrinho("P001", "Camiseta", new BigDecimal("49.90"), 2);
        when(carrinho.ver("5511")).thenReturn(new CarrinhoResumo(List.of(item), new BigDecimal("99.80")));
        when(estoque.consultar(anyString())).thenReturn(5);
        mvc.perform(get("/api/simulador/estado").param("telefone", "5511"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carrinho.total").value(99.80))
                .andExpect(jsonPath("$.estoque.length()").value(8))
                .andExpect(jsonPath("$.estoque[0].produtoId").value("P001"));
    }

    @Test
    void resetReiniciaTudoERetorna204() throws Exception {
        mvc.perform(post("/api/simulador/reset")).andExpect(status().isNoContent());
        verify(agente).reiniciarConversas();
        verify(carrinho).reiniciarTudo();
    }

    @Test
    void estadoSemTelefoneRetorna400() throws Exception {
        mvc.perform(get("/api/simulador/estado"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Requisição inválida."));
    }

    @Test
    void jsonMalformadoRetorna400() throws Exception {
        mvc.perform(post("/api/simulador/mensagens").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Requisição inválida."));
    }
}
