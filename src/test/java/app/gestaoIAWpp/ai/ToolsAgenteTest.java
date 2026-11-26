package app.gestaoIAWpp.ai;

import static app.gestaoIAWpp.ai.ContextoTool.CHAVE_CLIENTE_ID;
import static app.gestaoIAWpp.ai.ContextoTool.CHAVE_EMPRESA_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import app.gestaoIAWpp.carrinho.CarrinhoService;
import app.gestaoIAWpp.carrinho.CarrinhoTeste;
import app.gestaoIAWpp.carrinho.CarrinhoTool;
import app.gestaoIAWpp.produto.CatalogoTeste;
import app.gestaoIAWpp.produto.ProdutoService;
import app.gestaoIAWpp.produto.ProdutoTool;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.ToolExecutionException;

class ToolsAgenteTest {

    private static final UUID EMPRESA = UUID.randomUUID();
    private static final UUID CLIENTE = UUID.randomUUID();

    private ProdutoService produtos;
    private CarrinhoService carrinho;
    private ToolRegistry registry;
    private CarrinhoTool carrinhoTool;
    private ToolContext ctx;
    private UUID pretaM;
    private String pretaMTexto;

    @BeforeEach
    void setUp() {
        var catalogo = CatalogoTeste.lojaDemo(EMPRESA);
        produtos = spy(catalogo.produtoService());
        carrinho = CarrinhoTeste.servico(produtos);
        carrinhoTool = new CarrinhoTool(carrinho);
        registry = new ToolRegistry(List.of(
                new ProdutoTool(produtos), carrinhoTool));
        ctx = new ToolContext(Map.of(CHAVE_EMPRESA_ID, EMPRESA, CHAVE_CLIENTE_ID, CLIENTE));
        pretaM = catalogo.variante("Camisa Básica (Preta / M)").getId();
        pretaMTexto = pretaM.toString();
    }

    private ToolCallback callback(String nome) {
        return Arrays.stream(registry.callbacks())
                .filter(c -> c.getToolDefinition().name().equals(nome))
                .findFirst().orElseThrow();
    }

    @Test
    void adicionarUsaClienteDoContextoEBaixaEstoque() {
        carrinhoTool.adicionarAoCarrinho(pretaMTexto, 2, ctx);
        assertThat(carrinho.ver(CLIENTE).itens()).singleElement()
                .satisfies(i -> assertThat(i.quantidade()).isEqualTo(2));
        verify(produtos).baixarEstoque(pretaM, 2);
    }

    @Test
    void removerSemQuantidadeRemoveTudo() {
        carrinhoTool.adicionarAoCarrinho(pretaMTexto, 2, ctx);
        carrinhoTool.removerDoCarrinho(pretaMTexto, null, ctx);
        assertThat(carrinho.ver(CLIENTE).itens()).isEmpty();
        verify(produtos).devolverEstoque(pretaM, 2);
    }

    @Test
    void quantidadeZeroLancaSemBaixa() {
        assertThatThrownBy(() -> carrinhoTool.adicionarAoCarrinho(pretaMTexto, 0, ctx))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Quantidade deve ser maior que zero.");
        verify(produtos, never()).baixarEstoque(any(), anyInt());
    }

    @Test
    void quantidadeAusenteLancaSemBaixa() {
        assertThatThrownBy(() -> carrinhoTool.adicionarAoCarrinho(pretaMTexto, null, ctx))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Parâmetro obrigatório ausente: quantidade.");
        verify(produtos, never()).baixarEstoque(any(), anyInt());
    }

    @Test
    void semClienteNoContextoFalhaComMensagemClara() {
        var semCliente = new ToolContext(Map.of(CHAVE_EMPRESA_ID, EMPRESA));
        assertThatThrownBy(() -> carrinhoTool.adicionarAoCarrinho(pretaMTexto, 1, semCliente))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cliente ausente");
        verify(produtos, never()).baixarEstoque(any(), anyInt());
    }

    @Test
    void callbackComFormatoInvalidoLancaToolExecutionException() {
        assertThatThrownBy(() -> callback("adicionar_ao_carrinho")
                .call("{\"varianteId\":\"" + pretaMTexto + "\",\"quantidade\":\"dois\"}", ctx))
                .isInstanceOf(ToolExecutionException.class);
        verify(produtos, never()).baixarEstoque(any(), anyInt());
    }
}
