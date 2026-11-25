package app.gestaoIAWpp.empresa;

public class NumeroNaoCadastradoException extends IllegalArgumentException {

	public NumeroNaoCadastradoException() {
		super("Número de atendimento não cadastrado.");
	}

}
