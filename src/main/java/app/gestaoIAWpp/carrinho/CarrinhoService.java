package app.gestaoIAWpp.carrinho;

import app.gestaoIAWpp.produto.ProdutoService;
import app.gestaoIAWpp.produto.VarianteResumo;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carrinho no banco, um ABERTO por cliente. Cada operação que escreve roda numa única transação:
 * estoque e carrinho mudam juntos ou nada muda (RN02).
 *
 * <p>O estoque e os dados da variante vêm do {@link ProdutoService}; o carrinho guarda só o id da
 * variante. As queries nativas de escrita ({@code baixarEstoque} e {@code devolverEstoque}, no
 * {@code produto}, e {@code somarItem}) limpam o contexto de persistência ao terminar. Por isso o
 * resumo é sempre montado com o carrinho lido de novo depois delas: entidades carregadas antes ficam
 * destacadas.
 *
 * <p>Concorrência: quem mexe nos itens ({@code adicionar}, {@code remover}) e o reset travam o carrinho
 * ({@code SELECT ... FOR UPDATE}) antes de ler ou alterar itens, então não se intercalam no mesmo
 * carrinho (RN14). A ordem das travas é sempre carrinho e depois variante, para não haver deadlock.
 */
@Service
public class CarrinhoService {

    private static final String QUANTIDADE_INVALIDA = "Quantidade deve ser maior que zero.";
    private static final CarrinhoResumo VAZIO = new CarrinhoResumo(List.of(), BigDecimal.ZERO.setScale(2));

    private final ProdutoService produtos;
    private final CarrinhoRepository carrinhos;

    public CarrinhoService(ProdutoService produtos, CarrinhoRepository carrinhos) {
        this.produtos = produtos;
        this.carrinhos = carrinhos;
    }

    @Transactional
    public CarrinhoResumo adicionar(UUID empresaId, UUID clienteId, UUID varianteId, int quantidade) {
        if (quantidade < 1) {
            throw new IllegalArgumentException(QUANTIDADE_INVALIDA);
        }
        // Só valida que a variante existe, está ativa e é da empresa; falha com VarianteNaoEncontradaException.
        produtos.consultarDisponibilidade(empresaId, varianteId);
        // Trava o carrinho que já existe antes de baixar o estoque (ordem carrinho, depois variante).
        Optional<Carrinho> existente = carrinhoAbertoTravado(empresaId, clienteId);
        produtos.baixarEstoque(varianteId, quantidade);
        // Sem carrinho, só cria depois de saber que há estoque: assim a falha não deixa carrinho vazio.
        Carrinho carrinho = existente.orElseGet(() -> criarETravar(empresaId, clienteId));
        carrinhos.somarItem(carrinho.getId(), varianteId, quantidade);
        return resumoDoAberto(empresaId, clienteId);
    }

    /** RN03: sem quantidade, ou com quantidade maior ou igual à do item, remove o item inteiro. */
    @Transactional
    public CarrinhoResumo remover(UUID empresaId, UUID clienteId, UUID varianteId, Integer quantidade) {
        if (quantidade != null && quantidade < 1) {
            throw new IllegalArgumentException(QUANTIDADE_INVALIDA);
        }
        Carrinho carrinho = carrinhoAbertoTravado(empresaId, clienteId)
                .orElseThrow(() -> new ItemNaoEstaNoCarrinhoException(varianteId));
        ItemCarrinho item = carrinho.item(varianteId)
                .orElseThrow(() -> new ItemNaoEstaNoCarrinhoException(varianteId));
        int removida = (quantidade == null || quantidade >= item.getQuantidade()) ? item.getQuantidade() : quantidade;
        if (removida == item.getQuantidade()) {
            carrinho.tirar(item);
        } else {
            item.diminuir(removida);
        }
        // O UPDATE do estoque (flushAutomatically, no produto) grava a mudança do item antes; depois o contexto é limpo.
        produtos.devolverEstoque(varianteId, removida);
        return resumoDoAberto(empresaId, clienteId);
    }

    @Transactional(readOnly = true)
    public CarrinhoResumo ver(UUID clienteId) {
        return carrinhos.findByClienteIdAndStatus(clienteId, StatusCarrinho.ABERTO)
                .map(this::resumir)
                .orElse(VAZIO);
    }

    /**
     * RN14: devolve ao estoque os itens dos carrinhos ABERTO da empresa e apaga esses carrinhos (os
     * itens vão junto pelo ON DELETE CASCADE). Os carrinhos são travados antes de ler os itens: um
     * {@code adicionar} em andamento termina antes, e o reset devolve a quantidade já somada. As
     * devoluções são lidas antes do primeiro UPDATE, que destaca as entidades e impediria carregar os
     * itens ainda não lidos.
     */
    @Transactional
    public void reiniciarDemo(UUID empresaId) {
        List<Carrinho> abertos = carrinhos.travarDaEmpresa(empresaId, StatusCarrinho.ABERTO);
        List<Devolucao> devolucoes = abertos.stream()
                .flatMap(c -> c.getItens().stream())
                .map(i -> new Devolucao(i.getVarianteId(), i.getQuantidade()))
                .toList();
        List<UUID> ids = abertos.stream().map(Carrinho::getId).toList();
        devolucoes.forEach(d -> produtos.devolverEstoque(d.varianteId(), d.quantidade()));
        carrinhos.deleteAllByIdInBatch(ids);
    }

    /** O carrinho ABERTO do cliente, só se for da empresa informada. */
    private Optional<Carrinho> carrinhoAberto(UUID empresaId, UUID clienteId) {
        return carrinhos.findByClienteIdAndStatus(clienteId, StatusCarrinho.ABERTO)
                .filter(c -> c.getEmpresaId().equals(empresaId));
    }

    /** Como {@link #carrinhoAberto}, mas travado até o fim da transação. */
    private Optional<Carrinho> carrinhoAbertoTravado(UUID empresaId, UUID clienteId) {
        return carrinhos.travarDoCliente(clienteId, StatusCarrinho.ABERTO)
                .filter(c -> c.getEmpresaId().equals(empresaId));
    }

    /**
     * Cria (se preciso) e trava o carrinho ABERTO. Se um reset apagou o carrinho entre o INSERT e a
     * trava (o INSERT não criou porque o carrinho existia e o reset o segurava), cria de novo, uma vez.
     */
    private Carrinho criarETravar(UUID empresaId, UUID clienteId) {
        carrinhos.criarAbertoSeNaoExiste(empresaId, clienteId);
        return carrinhoAbertoTravado(empresaId, clienteId).orElseGet(() -> {
            carrinhos.criarAbertoSeNaoExiste(empresaId, clienteId);
            return carrinhoAbertoTravado(empresaId, clienteId).orElseThrow();
        });
    }

    private CarrinhoResumo resumoDoAberto(UUID empresaId, UUID clienteId) {
        return carrinhoAberto(empresaId, clienteId).map(this::resumir).orElse(VAZIO);
    }

    /**
     * RN08: preço atual da variante, lido agora no {@code produto}; o item não guarda preço. RN09: item
     * cuja variante não volta do resumo é estado inconsistente.
     */
    private CarrinhoResumo resumir(Carrinho carrinho) {
        List<UUID> ids = carrinho.getItens().stream().map(ItemCarrinho::getVarianteId).toList();
        Map<UUID, VarianteResumo> variantes = produtos.resumirVariantes(ids);
        List<ItemCarrinhoResumo> itens = carrinho.getItens().stream()
                .map(i -> {
                    VarianteResumo v = variantes.get(i.getVarianteId());
                    if (v == null) {
                        throw new IllegalStateException("Variante " + i.getVarianteId() + " do carrinho não existe.");
                    }
                    return new ItemCarrinhoResumo(i.getVarianteId(), v.nome(), v.preco(), i.getQuantidade());
                })
                .toList();
        BigDecimal total = itens.stream()
                .map(ItemCarrinhoResumo::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2);
        return new CarrinhoResumo(itens, total);
    }

    private record Devolucao(UUID varianteId, int quantidade) {
    }
}
