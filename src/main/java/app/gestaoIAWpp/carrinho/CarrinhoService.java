package app.gestaoIAWpp.carrinho;

import app.gestaoIAWpp.produto.EstoqueService;
import app.gestaoIAWpp.produto.Produto;
import app.gestaoIAWpp.produto.ProdutoNaoEncontradoException;
import app.gestaoIAWpp.produto.ProdutoRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Carrinhos em memória, um por telefone. Adicionar reserva estoque e remover devolve.
 * Os métodos que mudam estado são synchronized para manter carrinho e estoque coerentes.
 */
@Service
public class CarrinhoService {

    private final ProdutoRepository produtos;
    private final EstoqueService estoque;
    private final Map<String, LinkedHashMap<String, ItemCarrinho>> carrinhos = new HashMap<>();

    public CarrinhoService(ProdutoRepository produtos, EstoqueService estoque) {
        this.produtos = produtos;
        this.estoque = estoque;
    }

    public synchronized CarrinhoResumo adicionar(String telefone, String produtoId, int quantidade) {
        Produto produto = produtos.buscarPorId(produtoId)
                .orElseThrow(() -> new ProdutoNaoEncontradoException(produtoId));
        estoque.baixar(produtoId, quantidade);
        var itens = carrinhos.computeIfAbsent(telefone, t -> new LinkedHashMap<>());
        itens.merge(
                produtoId,
                new ItemCarrinho(produto.id(), produto.nome(), produto.preco(), quantidade),
                (atual, novo) -> new ItemCarrinho(
                        atual.produtoId(), atual.nome(), atual.precoUnitario(), atual.quantidade() + quantidade));
        return resumir(itens);
    }

    public synchronized CarrinhoResumo remover(String telefone, String produtoId, Integer quantidade) {
        if (quantidade != null && quantidade < 1) {
            throw new IllegalArgumentException("Quantidade deve ser maior que zero.");
        }
        var itens = carrinhos.getOrDefault(telefone, new LinkedHashMap<>());
        ItemCarrinho item = itens.get(produtoId);
        if (item == null) {
            throw new ItemNaoEstaNoCarrinhoException(produtoId);
        }
        int removida = (quantidade == null || quantidade >= item.quantidade()) ? item.quantidade() : quantidade;
        estoque.devolver(produtoId, removida);
        if (removida == item.quantidade()) {
            itens.remove(produtoId);
        } else {
            itens.put(produtoId, new ItemCarrinho(
                    item.produtoId(), item.nome(), item.precoUnitario(), item.quantidade() - removida));
        }
        return resumir(itens);
    }

    public synchronized CarrinhoResumo ver(String telefone) {
        return resumir(carrinhos.getOrDefault(telefone, new LinkedHashMap<>()));
    }

    /** Não devolve estoque: o reset da demo reinicia o estoque à parte. */
    public synchronized void limparTodos() {
        carrinhos.clear();
    }

    private CarrinhoResumo resumir(Map<String, ItemCarrinho> itens) {
        var lista = new ArrayList<>(itens.values());
        BigDecimal total = lista.stream()
                .map(ItemCarrinho::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2);
        return new CarrinhoResumo(lista, total);
    }
}
