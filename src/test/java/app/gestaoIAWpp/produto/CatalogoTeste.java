package app.gestaoIAWpp.produto;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Monta entidades do catálogo em memória para os testes unitários (sem banco) e repositórios
 * mockados por cima delas. Os ids são determinísticos dentro de uma instância.
 */
public final class CatalogoTeste {

    private final UUID empresaId;
    private final List<Produto> produtos = new ArrayList<>();
    private long sequencia;

    public CatalogoTeste(UUID empresaId) {
        this.empresaId = empresaId;
    }

    /** O mesmo catálogo do seed da Loja Demo (V2), com ids determinísticos. */
    public static CatalogoTeste lojaDemo(UUID empresaId) {
        CatalogoTeste c = new CatalogoTeste(empresaId);
        Produto basica = c.produto("Camisa Básica", "Camisa de algodão, gola redonda", "camisa");
        ValorOpcao[] cor = c.opcao(basica, "Cor", 0, "Preta", "Branca");
        ValorOpcao[] tamanho = c.opcao(basica, "Tamanho", 1, "M", "G");
        c.variante(basica, "49.90", 12, cor[0], tamanho[0]);
        c.variante(basica, "49.90", 0, cor[0], tamanho[1]);
        c.variante(basica, "49.90", 8, cor[1], tamanho[0]);

        Produto polo = c.produto("Camisa Polo", "Camisa polo de piquet", "camisa");
        c.variante(polo, "79.90", 5, c.opcao(polo, "Cor", 0, "Azul")[0], c.opcao(polo, "Tamanho", 1, "M")[0]);

        Produto masculina = c.produto("Calça Jeans Masculina", "Calça jeans reta, lavagem escura", "calça");
        c.variante(masculina, "149.90", 3, c.opcao(masculina, "Tamanho", 0, "42")[0]);

        Produto feminina = c.produto("Calça Jeans Feminina", "Calça jeans skinny, lavagem clara", "calça");
        c.variante(feminina, "139.90", 6, c.opcao(feminina, "Tamanho", 0, "38")[0]);

        Produto tenis = c.produto("Tênis Casual", "Tênis de couro sintético", "tênis");
        c.variante(tenis, "199.90", 4, c.opcao(tenis, "Cor", 0, "Branco")[0], c.opcao(tenis, "Tamanho", 1, "40")[0]);

        Produto bone = c.produto("Boné Ajustável", "Boné preto de aba curva com regulagem", "acessório");
        c.variante(bone, "39.90", 15);
        return c;
    }

    public UUID empresaId() {
        return empresaId;
    }

    public List<Produto> produtos() {
        return produtos;
    }

    public Produto produto(String nome, String descricao, String categoria) {
        Produto p = new Produto(proximoId(), empresaId, nome, descricao, categoria, true);
        produtos.add(p);
        return p;
    }

    public ValorOpcao[] opcao(Produto produto, String nome, int posicao, String... valores) {
        Opcao opcao = new Opcao(proximoId(), produto, nome, posicao);
        produto.adicionarOpcao(opcao);
        ValorOpcao[] criados = new ValorOpcao[valores.length];
        for (int i = 0; i < valores.length; i++) {
            criados[i] = new ValorOpcao(proximoId(), opcao, valores[i], i);
            opcao.adicionarValor(criados[i]);
        }
        return criados;
    }

    public Variante variante(Produto produto, String preco, int estoque, ValorOpcao... valores) {
        return variante(produto, preco, estoque, true, valores);
    }

    public Variante varianteInativa(Produto produto, String preco, int estoque, ValorOpcao... valores) {
        return variante(produto, preco, estoque, false, valores);
    }

    private Variante variante(Produto produto, String preco, int estoque, boolean ativo, ValorOpcao... valores) {
        Variante v = new Variante(proximoId(), produto, null, new BigDecimal(preco), estoque, ativo,
                new LinkedHashSet<>(Arrays.asList(valores)));
        produto.adicionarVariante(v);
        return v;
    }

    /** Variante do produto com o nome dado cujo nome completo bate com o informado. */
    public Variante variante(String nomeCompleto) {
        return produtos.stream()
                .flatMap(p -> p.getVariantes().stream())
                .filter(v -> v.nomeCompleto().equals(nomeCompleto))
                .findFirst().orElseThrow();
    }

    /** {@code catalogoAtivo(empresaId)} devolve os produtos ativos deste catálogo. */
    public ProdutoRepository produtoRepository() {
        ProdutoRepository repo = mock(ProdutoRepository.class);
        when(repo.catalogoAtivo(empresaId)).thenReturn(produtos.stream().filter(Produto::isAtivo).toList());
        return repo;
    }

    /**
     * {@code daEmpresa} acha a variante ativa da empresa; {@code baixarEstoque} afeta 1 linha se houver
     * saldo (sem alterar a entidade); {@code devolverEstoque} afeta 1 linha; {@code findById} acha a variante.
     */
    public VarianteRepository varianteRepository() {
        VarianteRepository repo = mock(VarianteRepository.class);
        when(repo.daEmpresa(any(), any())).thenAnswer(inv -> daEmpresa(inv.getArgument(0), inv.getArgument(1)));
        when(repo.findById(any())).thenAnswer(inv -> porId(inv.getArgument(0)));
        when(repo.baixarEstoque(any(), anyInt())).thenAnswer(inv -> porId(inv.getArgument(0))
                .filter(v -> v.getQuantidadeEstoque() >= (int) inv.getArgument(1))
                .map(v -> 1).orElse(0));
        when(repo.devolverEstoque(any(), anyInt())).thenReturn(1);
        return repo;
    }

    private Optional<Variante> daEmpresa(UUID varianteId, UUID empresa) {
        return porId(varianteId)
                .filter(v -> v.isAtivo() && v.getProduto().isAtivo() && v.getProduto().getEmpresaId().equals(empresa));
    }

    private Optional<Variante> porId(UUID varianteId) {
        return produtos.stream()
                .flatMap(p -> p.getVariantes().stream())
                .filter(v -> v.getId().equals(varianteId))
                .findFirst();
    }

    private UUID proximoId() {
        return new UUID(0L, ++sequencia);
    }
}
