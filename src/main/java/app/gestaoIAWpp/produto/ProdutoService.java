package app.gestaoIAWpp.produto;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regra de negócio da busca de produtos (RN09) e da consulta de disponibilidade, independente da IA.
 * A busca roda em Java sobre o catálogo ativo da empresa (D10): o catálogo da demo é pequeno.
 */
@Service
@Transactional(readOnly = true)
public class ProdutoService {

    static final int LIMITE_RESULTADOS_BUSCA = 5;

    private static final Set<String> STOPWORDS = Set.of("de", "da", "do", "das", "dos", "em", "com", "para", "um", "uma");
    private static final int PESO_NOME = 2;
    private static final int PESO_OUTROS_CAMPOS = 1;

    /** Ordem das variantes dentro do produto: pela posição de cada valor, na ordem das opções. */
    private static final Comparator<Variante> ORDEM_VARIANTES = Comparator
            .comparing((Variante v) -> v.valoresOrdenados().stream().mapToInt(ValorOpcao::getPosicao).toArray(),
                    Arrays::compare)
            .thenComparing(Variante::getId);

    /** Ordem do painel: nome do produto, depois a descrição das opções (sem opções primeiro). */
    private static final Comparator<Variante> ORDEM_LISTAGEM = Comparator
            .comparing((Variante v) -> normalizar(v.getProduto().getNome()))
            .thenComparing(Variante::opcoesDescritas, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(Variante::getId);

    private final ProdutoRepository produtos;
    private final VarianteRepository variantes;

    ProdutoService(ProdutoRepository produtos, VarianteRepository variantes) {
        this.produtos = produtos;
        this.variantes = variantes;
    }

    public ResultadoBusca buscar(UUID empresaId, String termo) {
        List<String> palavras = palavras(termo);
        List<Encontrado> encontrados = palavras.isEmpty() ? List.of() : produtos.catalogoAtivo(empresaId).stream()
                .map(p -> new Encontrado(p, variantesAtivas(p)))
                .filter(e -> !e.variantes().isEmpty())
                .map(e -> e.comPontuacao(pontuar(e, palavras)))
                .filter(e -> e.pontos() > 0)
                .sorted(Comparator.comparingInt(Encontrado::pontos).reversed()
                        .thenComparing(Encontrado::semEstoque)
                        .thenComparing(e -> normalizar(e.produto().getNome()))
                        .thenComparing(e -> e.produto().getId()))
                .toList();

        List<ProdutoResumo> lista = encontrados.stream()
                .limit(LIMITE_RESULTADOS_BUSCA)
                .map(ProdutoService::resumo)
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

    public DisponibilidadeVariante consultarDisponibilidade(UUID empresaId, UUID varianteId) {
        Variante v = varianteDaEmpresa(empresaId, varianteId);
        return new DisponibilidadeVariante(v.getId(), v.nomeCompleto(), v.opcoesDescritas(), v.emEstoque(),
                v.getQuantidadeEstoque());
    }

    /** Todas as variantes ativas da empresa, por nome do produto e depois opções (painel do simulador). */
    public List<VarianteResumo> listarVariantes(UUID empresaId) {
        return produtos.catalogoAtivo(empresaId).stream()
                .flatMap(p -> variantesAtivas(p).stream())
                .sorted(ORDEM_LISTAGEM)
                .map(VarianteResumo::de)
                .toList();
    }

    Variante varianteDaEmpresa(UUID empresaId, UUID varianteId) {
        return variantes.daEmpresa(varianteId, empresaId)
                .orElseThrow(() -> new VarianteNaoEncontradaException(varianteId.toString()));
    }

    /**
     * RN06: baixa atômica do estoque. Sem saldo, o nome e o saldo da exceção são lidos depois da falha.
     * Participa da transação do chamador (o carrinho grava o item na mesma transação).
     */
    @Transactional
    public void baixarEstoque(UUID varianteId, int quantidade) {
        if (variantes.baixarEstoque(varianteId, quantidade) == 0) {
            Variante v = variantes.findById(varianteId)
                    .orElseThrow(() -> new VarianteNaoEncontradaException(varianteId.toString()));
            throw new EstoqueInsuficienteException(varianteId, v.nomeCompleto(), quantidade, v.getQuantidadeEstoque());
        }
    }

    /** RN07: devolve o estoque de forma atômica. */
    @Transactional
    public void devolverEstoque(UUID varianteId, int quantidade) {
        variantes.devolverEstoque(varianteId, quantidade);
    }

    /**
     * RN08: resumo das variantes pelo id, com o preço atual, incluindo as desativadas depois de entrar no carrinho.
     * Ids inexistentes ficam fora do mapa.
     */
    public Map<UUID, VarianteResumo> resumirVariantes(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return variantes.resumoDasVariantes(ids).stream()
                .collect(Collectors.toMap(Variante::getId, VarianteResumo::de));
    }

    private static List<Variante> variantesAtivas(Produto p) {
        return p.getVariantes().stream()
                .filter(Variante::isAtivo)
                .sorted(ORDEM_VARIANTES)
                .toList();
    }

    private static ProdutoResumo resumo(Encontrado e) {
        Produto p = e.produto();
        return new ProdutoResumo(p.getId(), p.getNome(), p.getDescricao(),
                e.variantes().stream().map(VarianteResumo::de).toList());
    }

    /** RN09: 2 pontos por palavra no nome; senão 1 se estiver na descrição, na categoria ou num valor de opção. */
    private static int pontuar(Encontrado e, List<String> palavras) {
        Produto p = e.produto();
        String nome = normalizar(p.getNome());
        String outros = normalizar(p.getDescricao()) + " " + normalizar(p.getCategoria()) + " "
                + e.variantes().stream()
                        .flatMap(v -> v.getValores().stream())
                        .map(vo -> normalizar(vo.getValor()))
                        .collect(Collectors.joining(" "));
        int pontos = 0;
        for (String palavra : palavras) {
            if (nome.contains(palavra)) {
                pontos += PESO_NOME;
            } else if (outros.contains(palavra)) {
                pontos += PESO_OUTROS_CAMPOS;
            }
        }
        return pontos;
    }

    /** Termo normalizado, em palavras de 2+ caracteres, sem stopwords. */
    private static List<String> palavras(String termo) {
        if (termo == null) {
            return List.of();
        }
        return Arrays.stream(normalizar(termo).split("[^\\p{L}\\p{N}]+"))
                .filter(p -> p.length() >= 2 && !STOPWORDS.contains(p))
                .toList();
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim();
    }

    private record Encontrado(Produto produto, List<Variante> variantes, int pontos) {

        Encontrado(Produto produto, List<Variante> variantes) {
            this(produto, variantes, 0);
        }

        Encontrado comPontuacao(int pontuacao) {
            return new Encontrado(produto, variantes, pontuacao);
        }

        /** 0 se alguma variante tem estoque, 1 se nenhuma: com estoque vem antes no desempate. */
        int semEstoque() {
            return variantes.stream().anyMatch(Variante::emEstoque) ? 0 : 1;
        }
    }
}
