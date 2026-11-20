package app.gestaoIAWpp.produto;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Estoque em memória da demo. Todos os métodos públicos são synchronized (inclusive as leituras)
 * para que a checagem de saldo e a baixa sejam uma operação atômica e as leituras vejam o valor atual.
 */
@Service
public class EstoqueService {

    private final Map<String, Integer> estoque = new HashMap<>(CatalogoMock.ESTOQUE_INICIAL);

    public synchronized int consultar(String produtoId) {
        Integer saldo = estoque.get(produtoId);
        if (saldo == null) {
            throw new ProdutoNaoEncontradoException(produtoId);
        }
        return saldo;
    }

    public synchronized void baixar(String produtoId, int quantidade) {
        validarQuantidade(quantidade);
        int disponivel = consultar(produtoId);
        if (quantidade > disponivel) {
            throw new EstoqueInsuficienteException(produtoId, quantidade, disponivel);
        }
        estoque.put(produtoId, disponivel - quantidade);
    }

    public synchronized void devolver(String produtoId, int quantidade) {
        validarQuantidade(quantidade);
        estoque.put(produtoId, consultar(produtoId) + quantidade);
    }

    public synchronized void reiniciar() {
        estoque.clear();
        estoque.putAll(CatalogoMock.ESTOQUE_INICIAL);
    }

    public synchronized Map<String, Integer> snapshot() {
        return Map.copyOf(estoque);
    }

    private void validarQuantidade(int quantidade) {
        if (quantidade < 1) {
            throw new IllegalArgumentException("Quantidade deve ser maior que zero.");
        }
    }
}
