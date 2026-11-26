/**
 * Fixture do ModulosTest: o módulo {@code origem} não declara nenhuma dependência permitida,
 * mas {@link app.gestaoIAWpp.fixture.invalida.origem.ServicoOrigem} usa o módulo {@code destino}.
 */
@ApplicationModule(allowedDependencies = {})
package app.gestaoIAWpp.fixture.invalida.origem;

import org.springframework.modulith.ApplicationModule;
