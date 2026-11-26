/** Módulo atendimento: entrada agnóstica de canal que resolve empresa e cliente e chama o agente. */
@ApplicationModule(allowedDependencies = {"empresa", "cliente", "ai"})
package app.gestaoIAWpp.atendimento;

import org.springframework.modulith.ApplicationModule;
