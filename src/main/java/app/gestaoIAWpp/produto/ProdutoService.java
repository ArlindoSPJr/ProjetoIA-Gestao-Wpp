package app.gestaoIAWpp.produto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/** Regra de negócio da busca de produtos e da consulta de disponibilidade, independente da IA. */
@Service
public class ProdutoService {

    static final int LIMITE_RESULTADOS_BUSCA = 5;

    private final ProdutoRepository produtos;
    private final EstoqueService estoque;

    public ProdutoService(ProdutoRepository produtos, EstoqueService estoque) {
        this.produtos = produtos;
        this.estoque = estoque;
    }

    public ResultadoBusca buscar(String termo) {
        List<ProdutoEncontrado> encontrados = new ArrayList<>(produtos.buscar(termo));
        encontrados.sort(Comparator
                .comparingInt(ProdutoEncontrado::pontuacao).reversed()
                .thenComparing(pe -> estoque.consultar(pe.produto().id()) > 0 ? 0 : 1)
                .thenComparing(pe -> pe.produto().id()));

        List<ProdutoResumo> lista = encontrados.stream()
                .limit(LIMITE_RESULTADOS_BUSCA)
                .map(pe -> resumo(pe.produto()))
                .toList();

        String mensagem = null;
        if (encontrados.isEmpty()) {
            mensagem = "Nenhum produto encontrado para '" + termo + "'.";
        } else if (encontrados.size() > LIMITE_RESULTADOS_BUSCA) {
            mensagem = "Foram encontrados " + encontrados.size() + " produtos para '" + termo
                    + "'; estes são os " + LIMITE_RESULTADOS_BUSCA + " mais relevantes. Peça ao cliente mais "
                    + "detalhes (modelo, cor ou tamanho) para refinar a busca.";
        }
        return new ResultadoBusca(lista, encontrados.size(), mensagem);
    }

    public DisponibilidadeProduto consultarDisponibilidade(String produtoId) {
        Produto p = produtos.buscarPorId(produtoId)
                .orElseThrow(() -> new ProdutoNaoEncontradoException(produtoId));
        int disponivel = estoque.consultar(produtoId);
        return new DisponibilidadeProduto(p.id(), p.nome(), disponivel > 0, disponivel);
    }

    private ProdutoResumo resumo(Produto p) {
        int disponivel = estoque.consultar(p.id());
        return new ProdutoResumo(p.id(), p.nome(), p.descricao(), p.preco(), disponivel > 0, disponivel);
    }
}
