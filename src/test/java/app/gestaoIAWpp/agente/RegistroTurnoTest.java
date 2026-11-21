package app.gestaoIAWpp.agente;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RegistroTurnoTest {

    @Test
    void iteracoesNaOrdemComDadosDaResposta() {
        RegistroTurno registro = new RegistroTurno();
        List<ToolPedida> tools = List.of(new ToolPedida("ver_carrinho", "{}"));

        assertThat(registro.iniciarIteracao()).isEqualTo(1);
        registro.concluirIteracao("tool_use", 10, 5, tools);
        assertThat(registro.iniciarIteracao()).isEqualTo(2);
        registro.concluirIteracao("end_turn", 20, 8, List.of());

        assertThat(registro.iteracoesIniciadas()).isEqualTo(2);
        assertThat(registro.iteracoes()).containsExactly(
                new IteracaoTurno(1, "tool_use", 10, 5, tools, null),
                new IteracaoTurno(2, "end_turn", 20, 8, List.of(), null));
    }

    @Test
    void observacaoVaiParaAUltimaIteracao() {
        RegistroTurno registro = new RegistroTurno();
        registro.iniciarIteracao();
        registro.concluirIteracao("tool_use", 1, 1, List.of());
        registro.iniciarIteracao();
        registro.concluirIteracao("end_turn", 2, 2, List.of());
        registro.observar("limite atingido");

        List<IteracaoTurno> iteracoes = registro.iteracoes();
        assertThat(iteracoes.get(0).observacao()).isNull();
        assertThat(iteracoes.get(1).observacao()).isEqualTo("limite atingido");
        assertThat(iteracoes.get(1).finishReason()).isEqualTo("end_turn");
    }

    @Test
    void iteracaoIniciadaSemConclusaoApareceComNumero() {
        RegistroTurno registro = new RegistroTurno();
        registro.iniciarIteracao();
        registro.observar("falha");

        assertThat(registro.iteracoes()).hasSize(1);
        IteracaoTurno it = registro.iteracoes().get(0);
        assertThat(it.numero()).isEqualTo(1);
        assertThat(it.finishReason()).isNull();
        assertThat(it.observacao()).isEqualTo("falha");
        assertThat(it.toolsPedidas()).isEmpty();
    }

    @Test
    void observarSemIteracaoNaoLancaENaoRegistra() {
        RegistroTurno registro = new RegistroTurno();

        registro.observar("falha");

        assertThat(registro.iteracoes()).isEmpty();
    }

    @Test
    void deDevolveRegistroOuNull() {
        RegistroTurno registro = new RegistroTurno();

        assertThat(RegistroTurno.de(Map.of(RegistroTurno.CHAVE, registro))).isSameAs(registro);
        assertThat(RegistroTurno.de(Map.of())).isNull();
    }
}
