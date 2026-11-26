package app.gestaoIAWpp.fixture.invalida.origem;

import app.gestaoIAWpp.fixture.invalida.destino.ServicoDestino;

/** Fixture do ModulosTest: dependência não declarada de {@code origem} em {@code destino}. */
public class ServicoOrigem {

	private final ServicoDestino destino = new ServicoDestino();

	public String nomeDoDestino() {
		return destino.nome();
	}
}
