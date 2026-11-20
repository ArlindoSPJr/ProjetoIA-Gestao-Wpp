package app.gestaoIAWpp.produto;

import java.util.List;
import java.util.Optional;

public interface ProdutoRepository {

    Optional<Produto> buscarPorId(String id);

    /**
     * Retorna todos os produtos que casam com o termo, com a pontuação de relevância,
     * ordenados por pontuação decrescente e depois por id. Não limita a quantidade.
     */
    List<ProdutoEncontrado> buscar(String termo);

    List<Produto> listarTodos();
}
