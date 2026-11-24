package app.gestaoIAWpp.carrinho;

import app.gestaoIAWpp.produto.EstoqueInsuficienteException;
import app.gestaoIAWpp.produto.Variante;
import app.gestaoIAWpp.produto.VarianteNaoEncontradaException;
import app.gestaoIAWpp.produto.VarianteRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carrinho no banco, um ABERTO por cliente. Cada operação que escreve roda numa única transação:
 * estoque e carrinho mudam juntos ou nada muda (RN02).
 *
 * <p>As queries nativas de escrita ({@code baixarEstoque}, {@code devolverEstoque}, {@code somarItem})
 * limpam o contexto de persistência ao terminar. Por isso o resumo é sempre montado com o carrinho
 * lido de novo depois delas: entidades carregadas antes ficam destacadas e com estoque antigo.
 */
@Service
public class CarrinhoService {

    private static final String QUANTIDADE_INVALIDA = "Quantidade deve ser maior que zero.";
    private static final CarrinhoResumo VAZIO = new CarrinhoResumo(List.of(), BigDecimal.ZERO.setScale(2));

    private final VarianteRepository variantes;
    private final CarrinhoRepository carrinhos;

    public CarrinhoService(VarianteRepository variantes, CarrinhoRepository carrinhos) {
        this.variantes = variantes;
        this.carrinhos = carrinhos;
    }

    @Transactional
    public CarrinhoResumo adicionar(UUID empresaId, UUID clienteId, UUID varianteId, int quantidade) {
        if (quantidade < 1) {
            throw new IllegalArgumentException(QUANTIDADE_INVALIDA);
        }
        Variante variante = variantes.daEmpresa(varianteId, empresaId)
                .orElseThrow(() -> new VarianteNaoEncontradaException(varianteId.toString()));
        String nome = variante.nomeCompleto();
        if (variantes.baixarEstoque(varianteId, quantidade) == 0) {
            int disponivel = variantes.findById(varianteId).map(Variante::getQuantidadeEstoque).orElse(0);
            throw new EstoqueInsuficienteException(varianteId, nome, quantidade, disponivel);
        }
        carrinhos.criarAbertoSeNaoExiste(empresaId, clienteId);
        Carrinho carrinho = carrinhoAberto(empresaId, clienteId).orElseThrow();
        carrinhos.somarItem(carrinho.getId(), varianteId, quantidade);
        return resumoDoAberto(empresaId, clienteId);
    }

    /** RN03: sem quantidade, ou com quantidade maior ou igual à do item, remove o item inteiro. */
    @Transactional
    public CarrinhoResumo remover(UUID empresaId, UUID clienteId, UUID varianteId, Integer quantidade) {
        if (quantidade != null && quantidade < 1) {
            throw new IllegalArgumentException(QUANTIDADE_INVALIDA);
        }
        Carrinho carrinho = carrinhoAberto(empresaId, clienteId)
                .orElseThrow(() -> new ItemNaoEstaNoCarrinhoException(varianteId));
        ItemCarrinho item = carrinho.item(varianteId)
                .orElseThrow(() -> new ItemNaoEstaNoCarrinhoException(varianteId));
        int removida = (quantidade == null || quantidade >= item.getQuantidade()) ? item.getQuantidade() : quantidade;
        if (removida == item.getQuantidade()) {
            carrinho.tirar(item);
        } else {
            item.diminuir(removida);
        }
        // flushAutomatically grava a mudança do item antes do UPDATE do estoque; depois o contexto é limpo.
        variantes.devolverEstoque(varianteId, removida);
        return resumoDoAberto(empresaId, clienteId);
    }

    @Transactional(readOnly = true)
    public CarrinhoResumo ver(UUID clienteId) {
        return carrinhos.findByClienteIdAndStatus(clienteId, StatusCarrinho.ABERTO)
                .map(CarrinhoService::resumir)
                .orElse(VAZIO);
    }

    /**
     * RN14: devolve ao estoque os itens dos carrinhos ABERTO da empresa e apaga esses carrinhos (os
     * itens vão junto pelo ON DELETE CASCADE). As devoluções são lidas antes do primeiro UPDATE, que
     * destaca as entidades e impediria carregar os itens ainda não lidos.
     */
    @Transactional
    public void reiniciarDemo(UUID empresaId) {
        List<Carrinho> abertos = carrinhos.findByEmpresaIdAndStatus(empresaId, StatusCarrinho.ABERTO);
        List<Devolucao> devolucoes = abertos.stream()
                .flatMap(c -> c.getItens().stream())
                .map(i -> new Devolucao(i.getVariante().getId(), i.getQuantidade()))
                .toList();
        List<UUID> ids = abertos.stream().map(Carrinho::getId).toList();
        devolucoes.forEach(d -> variantes.devolverEstoque(d.varianteId(), d.quantidade()));
        carrinhos.deleteAllByIdInBatch(ids);
    }

    /** O carrinho ABERTO do cliente, só se for da empresa informada. */
    private Optional<Carrinho> carrinhoAberto(UUID empresaId, UUID clienteId) {
        return carrinhos.findByClienteIdAndStatus(clienteId, StatusCarrinho.ABERTO)
                .filter(c -> c.getEmpresaId().equals(empresaId));
    }

    private CarrinhoResumo resumoDoAberto(UUID empresaId, UUID clienteId) {
        return carrinhoAberto(empresaId, clienteId).map(CarrinhoService::resumir).orElse(VAZIO);
    }

    /** RN08: preço atual da variante, lido agora; o item não guarda preço. */
    private static CarrinhoResumo resumir(Carrinho carrinho) {
        List<ItemCarrinhoResumo> itens = carrinho.getItens().stream()
                .map(i -> new ItemCarrinhoResumo(i.getVariante().getId(), i.getVariante().nomeCompleto(),
                        i.getVariante().getPreco(), i.getQuantidade()))
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
