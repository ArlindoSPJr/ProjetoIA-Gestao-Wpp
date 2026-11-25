package app.gestaoIAWpp.produto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProdutoServiceTest {

    private static final UUID EMPRESA = UUID.randomUUID();

    private static ProdutoService service(CatalogoTeste catalogo) {
        return new ProdutoService(catalogo.produtoRepository(), catalogo.varianteRepository());
    }

    @Test
    @DisplayName("T11 palavra no nome vale 2 e em valor de opção vale 1")
    void deveOrdenarPorPontuacao_quandoTermoCasaNomeEValorDeOpcao() {
        var r = service(CatalogoTeste.lojaDemo(EMPRESA)).buscar(EMPRESA, "camisa preta");

        assertThat(r.produtos()).extracting(ProdutoResumo::nome).containsExactly("Camisa Básica", "Camisa Polo");
        assertThat(r.totalEncontrado()).isEqualTo(2);
        assertThat(r.mensagem()).isNull();
    }

    @Test
    @DisplayName("T11 palavra só em valor de opção pontua 1 e basta para o produto aparecer")
    void deveEncontrarProduto_quandoTermoCasaSoValorDeOpcao() {
        var r = service(CatalogoTeste.lojaDemo(EMPRESA)).buscar(EMPRESA, "azul");

        assertThat(r.produtos()).extracting(ProdutoResumo::nome).containsExactly("Camisa Polo");
        assertThat(r.totalEncontrado()).isEqualTo(1);
    }

    @Test
    @DisplayName("T11 valor de opção que só existe em variante inativa não pontua")
    void deveIgnorarValorDeOpcao_quandoSoEmVarianteInativa() {
        var c = new CatalogoTeste(EMPRESA);
        Produto camisa = c.produto("Camisa Lisa", "algodão", "camisa");
        ValorOpcao[] cor = c.opcao(camisa, "Cor", 0, "Verde", "Azul");
        c.varianteInativa(camisa, "10.00", 5, cor[0]);
        c.variante(camisa, "10.00", 5, cor[1]);

        var r = service(c).buscar(EMPRESA, "verde");

        assertThat(r.produtos()).isEmpty();
        assertThat(r.totalEncontrado()).isZero();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"de", "a"})
    @DisplayName("T12 termo nulo, só stopword ou palavra de 1 letra não encontra nada")
    void deveDevolverVazioComMensagem_quandoTermoSemPalavraValida(String termo) {
        var r = service(CatalogoTeste.lojaDemo(EMPRESA)).buscar(EMPRESA, termo);

        assertThat(r.produtos()).isEmpty();
        assertThat(r.totalEncontrado()).isZero();
        assertThat(r.mensagem()).isEqualTo("Nenhum produto encontrado para '" + termo + "'.");
    }

    @Test
    @DisplayName("T13 mesma pontuação: produto com estoque vem antes do esgotado")
    void devePriorizarProdutoComEstoque_quandoMesmaPontuacao() {
        var c = new CatalogoTeste(EMPRESA);
        c.variante(c.produto("Tênis Alfa", null, null), "100.00", 0);
        c.variante(c.produto("Tênis Zeta", null, null), "100.00", 3);

        var r = service(c).buscar(EMPRESA, "tenis");

        assertThat(r.produtos()).extracting(ProdutoResumo::nome).containsExactly("Tênis Zeta", "Tênis Alfa");
    }

    @Test
    @DisplayName("T13 mesma pontuação e os dois com estoque: ordem alfabética do nome")
    void deveOrdenarPorNome_quandoMesmaPontuacaoEAmbosComEstoque() {
        var c = new CatalogoTeste(EMPRESA);
        c.variante(c.produto("Tênis Zeta", null, null), "100.00", 3);
        c.variante(c.produto("Tênis Alfa", null, null), "100.00", 1);

        var r = service(c).buscar(EMPRESA, "tenis");

        assertThat(r.produtos()).extracting(ProdutoResumo::nome).containsExactly("Tênis Alfa", "Tênis Zeta");
    }

    @Test
    @DisplayName("T14 exatamente 5 produtos que pontuam: todos, sem mensagem")
    void deveDevolverCincoSemMensagem_quandoCincoPontuam() {
        var r = service(catalogoDeCamisas(5)).buscar(EMPRESA, "camisa");

        assertThat(r.produtos()).hasSize(5);
        assertThat(r.totalEncontrado()).isEqualTo(5);
        assertThat(r.mensagem()).isNull();
    }

    @Test
    @DisplayName("T14 6 produtos que pontuam: os 5 primeiros e a mensagem de mais de 5")
    void deveLimitarACincoComMensagem_quandoSeisPontuam() {
        var r = service(catalogoDeCamisas(6)).buscar(EMPRESA, "camisa");

        assertThat(r.produtos()).extracting(ProdutoResumo::nome)
                .containsExactly("Camisa 1", "Camisa 2", "Camisa 3", "Camisa 4", "Camisa 5");
        assertThat(r.totalEncontrado()).isEqualTo(6);
        assertThat(r.mensagem()).isEqualTo("Foram encontrados 6 produtos para 'camisa'; estes são os 5 mais "
                + "relevantes. Peça ao cliente mais detalhes (modelo, cor ou tamanho) para refinar a busca.");
    }

    @Test
    @DisplayName("T15 produto com todas as variantes inativas fica fora do resultado e do total")
    void deveIgnorarProduto_quandoTodasAsVariantesInativas() {
        var c = new CatalogoTeste(EMPRESA);
        c.varianteInativa(c.produto("Camisa Velha", null, null), "10.00", 5);
        Produto nova = c.produto("Camisa Nova", null, null);
        c.variante(nova, "10.00", 5);
        c.varianteInativa(nova, "10.00", 5);

        var r = service(c).buscar(EMPRESA, "camisa");

        assertThat(r.produtos()).extracting(ProdutoResumo::nome).containsExactly("Camisa Nova");
        assertThat(r.produtos().get(0).variantes()).hasSize(1);
        assertThat(r.totalEncontrado()).isEqualTo(1);
    }

    @Test
    @DisplayName("T16 variante com opções descreve as opções na ordem; o nome leva os valores")
    void deveDescreverOpcoes_quandoVarianteTemValores() {
        var c = CatalogoTeste.lojaDemo(EMPRESA);
        Variante pretaM = c.variante("Camisa Básica (Preta / M)");

        var d = service(c).consultarDisponibilidade(EMPRESA, pretaM.getId());

        assertThat(d.opcoes()).isEqualTo("Cor: Preta, Tamanho: M");
        assertThat(d.nome()).isEqualTo("Camisa Básica (Preta / M)");
        assertThat(d.varianteId()).isEqualTo(pretaM.getId());
        assertThat(d.emEstoque()).isTrue();
        assertThat(d.quantidadeDisponivel()).isEqualTo(12);
    }

    @Test
    @DisplayName("T16 variante sem opções tem opcoes nulo e o nome é só o do produto")
    void deveTerOpcoesNulo_quandoVarianteSemValores() {
        var c = CatalogoTeste.lojaDemo(EMPRESA);
        Variante bone = c.produtos().stream().filter(p -> p.getNome().equals("Boné Ajustável"))
                .findFirst().orElseThrow().getVariantes().iterator().next();

        var d = service(c).consultarDisponibilidade(EMPRESA, bone.getId());

        assertThat(d.opcoes()).isNull();
        assertThat(d.nome()).isEqualTo("Boné Ajustável");
    }

    @Test
    @DisplayName("T20 variante que não é da empresa (ou não existe) não é encontrada")
    void deveLancarVarianteNaoEncontrada_quandoDaEmpresaVazio() {
        UUID id = UUID.randomUUID();
        var service = service(CatalogoTeste.lojaDemo(EMPRESA));

        assertThatThrownBy(() -> service.consultarDisponibilidade(EMPRESA, id))
                .isInstanceOf(VarianteNaoEncontradaException.class)
                .hasMessage("Variante " + id + " não encontrada.");
        assertThatThrownBy(() -> service.varianteDaEmpresa(EMPRESA, id))
                .isInstanceOf(VarianteNaoEncontradaException.class);
    }

    @Test
    @DisplayName("CA17 listarVariantes traz as variantes ativas ordenadas por produto e opções")
    void deveListarVariantesAtivasOrdenadas_quandoListaParaOSimulador() {
        var c = CatalogoTeste.lojaDemo(EMPRESA);
        c.varianteInativa(c.produtos().get(0), "49.90", 3);

        var lista = service(c).listarVariantes(EMPRESA);

        assertThat(lista).extracting(VarianteResumo::nome).containsExactly(
                "Boné Ajustável",
                "Calça Jeans Feminina (38)",
                "Calça Jeans Masculina (42)",
                "Camisa Básica (Branca / M)",
                "Camisa Básica (Preta / G)",
                "Camisa Básica (Preta / M)",
                "Camisa Polo (Azul / M)",
                "Tênis Casual (Branco / 40)");
    }

    @Test
    @DisplayName("T20 variante da empresa é devolvida por varianteDaEmpresa")
    void deveDevolverAVariante_quandoEhDaEmpresa() {
        var c = CatalogoTeste.lojaDemo(EMPRESA);
        Variante pretaM = c.variante("Camisa Básica (Preta / M)");

        assertThat(service(c).varianteDaEmpresa(EMPRESA, pretaM.getId())).isSameAs(pretaM);
    }

    @Test
    @DisplayName("RN09 variantes do produto vêm na ordem da posição dos valores (palavra na descrição pontua 1)")
    void deveOrdenarVariantesPelaPosicaoDosValores_quandoBuscaDevolveOProduto() {
        var r = service(CatalogoTeste.lojaDemo(EMPRESA)).buscar(EMPRESA, "algodao");

        assertThat(r.produtos()).singleElement().satisfies(p -> assertThat(p.variantes())
                .extracting(VarianteResumo::opcoes)
                .containsExactly("Cor: Preta, Tamanho: M", "Cor: Preta, Tamanho: G", "Cor: Branca, Tamanho: M"));
    }

    private static CatalogoTeste catalogoDeCamisas(int quantidade) {
        var c = new CatalogoTeste(EMPRESA);
        for (int i = quantidade; i >= 1; i--) {
            c.variante(c.produto("Camisa " + i, "desc", "camisa"), "10.00", 1);
        }
        return c;
    }
}
