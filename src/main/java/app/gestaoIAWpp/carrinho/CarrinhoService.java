package app.gestaoIAWpp.carrinho;

import app.gestaoIAWpp.produto.EstoqueInsuficienteException;
import app.gestaoIAWpp.produto.Variante;
import app.gestaoIAWpp.produto.VarianteNaoEncontradaException;
import app.gestaoIAWpp.produto.VarianteRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Carrinhos ainda em memória, um por cliente, com o estoque já no banco (baixa e devolução atômicas
 * pelo {@link VarianteRepository}). A assinatura é a final; a Task 5 leva o carrinho para o banco.
 * Os métodos são synchronized para manter carrinho e estoque coerentes.
 */
@Service
public class CarrinhoService {

    private static final String QUANTIDADE_INVALIDA = "Quantidade deve ser maior que zero.";

    private final VarianteRepository variantes;
    private final Map<UUID, LinkedHashMap<UUID, ItemCarrinhoResumo>> carrinhos = new HashMap<>();

    public CarrinhoService(VarianteRepository variantes) {
        this.variantes = variantes;
    }

    public synchronized CarrinhoResumo adicionar(UUID empresaId, UUID clienteId, UUID varianteId, int quantidade) {
        if (quantidade < 1) {
            throw new IllegalArgumentException(QUANTIDADE_INVALIDA);
        }
        Variante variante = variantes.daEmpresa(varianteId, empresaId)
                .orElseThrow(() -> new VarianteNaoEncontradaException(varianteId.toString()));
        if (variantes.baixarEstoque(varianteId, quantidade) == 0) {
            int disponivel = variantes.findById(varianteId).map(Variante::getQuantidadeEstoque).orElse(0);
            throw new EstoqueInsuficienteException(varianteId, variante.nomeCompleto(), quantidade, disponivel);
        }
        var itens = carrinhos.computeIfAbsent(clienteId, c -> new LinkedHashMap<>());
        itens.merge(
                varianteId,
                new ItemCarrinhoResumo(varianteId, variante.nomeCompleto(), variante.getPreco(), quantidade),
                (atual, novo) -> new ItemCarrinhoResumo(
                        atual.varianteId(), atual.nome(), atual.precoUnitario(), atual.quantidade() + quantidade));
        return resumir(itens);
    }

    /** Sem quantidade (ou com mais do que há no carrinho), remove todas as unidades da variante. */
    public synchronized CarrinhoResumo remover(UUID empresaId, UUID clienteId, UUID varianteId, Integer quantidade) {
        if (quantidade != null && quantidade < 1) {
            throw new IllegalArgumentException(QUANTIDADE_INVALIDA);
        }
        var itens = carrinhos.getOrDefault(clienteId, new LinkedHashMap<>());
        ItemCarrinhoResumo item = itens.get(varianteId);
        if (item == null) {
            throw new ItemNaoEstaNoCarrinhoException(varianteId);
        }
        int removida = (quantidade == null || quantidade >= item.quantidade()) ? item.quantidade() : quantidade;
        variantes.devolverEstoque(varianteId, removida);
        if (removida == item.quantidade()) {
            itens.remove(varianteId);
        } else {
            itens.put(varianteId, new ItemCarrinhoResumo(
                    item.varianteId(), item.nome(), item.precoUnitario(), item.quantidade() - removida));
        }
        return resumir(itens);
    }

    public synchronized CarrinhoResumo ver(UUID clienteId) {
        return resumir(carrinhos.getOrDefault(clienteId, new LinkedHashMap<>()));
    }

    /**
     * Reset da demo: devolve ao estoque as unidades de todos os carrinhos e esvazia-os, na mesma seção
     * crítica para um adicionar não cair no meio. O mapa em memória não guarda a empresa, então todos os
     * carrinhos são reiniciados; a Task 5 restringe à empresa.
     */
    public synchronized void reiniciarDemo(UUID empresaId) {
        for (var itens : carrinhos.values()) {
            for (ItemCarrinhoResumo item : itens.values()) {
                variantes.devolverEstoque(item.varianteId(), item.quantidade());
            }
        }
        carrinhos.clear();
    }

    private CarrinhoResumo resumir(Map<UUID, ItemCarrinhoResumo> itens) {
        var lista = new ArrayList<>(itens.values());
        BigDecimal total = lista.stream()
                .map(ItemCarrinhoResumo::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2);
        return new CarrinhoResumo(lista, total);
    }
}
