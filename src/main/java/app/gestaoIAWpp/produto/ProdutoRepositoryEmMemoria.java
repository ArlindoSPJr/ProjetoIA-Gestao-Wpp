package app.gestaoIAWpp.produto;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Repository;

/** Catálogo em memória (CatalogoMock) com busca por palavras, tolerante a acento e maiúscula. */
@Repository
public class ProdutoRepositoryEmMemoria implements ProdutoRepository {

    private static final Set<String> STOPWORDS = Set.of("de", "da", "do", "das", "dos", "em", "com", "para", "um", "uma");
    private static final int PESO_NOME = 2;
    private static final int PESO_DESCRICAO_OU_CATEGORIA = 1;

    private final List<Produto> produtos = CatalogoMock.PRODUTOS;

    @Override
    public Optional<Produto> buscarPorId(String id) {
        return produtos.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    @Override
    public List<ProdutoEncontrado> buscar(String termo) {
        if (termo == null) {
            return List.of();
        }
        List<String> palavras = Arrays.stream(normalizar(termo).split("\\s+"))
                .filter(p -> p.length() >= 2 && !STOPWORDS.contains(p))
                .toList();
        if (palavras.isEmpty()) {
            return List.of();
        }
        return produtos.stream()
                .map(p -> new ProdutoEncontrado(p, pontuar(p, palavras)))
                .filter(e -> e.pontuacao() > 0)
                .sorted(Comparator.comparingInt(ProdutoEncontrado::pontuacao).reversed()
                        .thenComparing(e -> e.produto().id()))
                .toList();
    }

    @Override
    public List<Produto> listarTodos() {
        return produtos;
    }

    private int pontuar(Produto produto, List<String> palavras) {
        String nome = normalizar(produto.nome());
        String descricaoECategoria = normalizar(produto.descricao()) + " " + normalizar(produto.categoria());
        int pontos = 0;
        for (String palavra : palavras) {
            if (nome.contains(palavra)) {
                pontos += PESO_NOME;
            } else if (descricaoECategoria.contains(palavra)) {
                pontos += PESO_DESCRICAO_OU_CATEGORIA;
            }
        }
        return pontos;
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim();
    }
}
