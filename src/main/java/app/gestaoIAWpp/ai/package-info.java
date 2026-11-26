/** Módulo ai: o agente de IA atrás da porta AgenteAtendimento, com suas tools. Depende de produto e carrinho. */
@ApplicationModule(allowedDependencies = {"produto", "carrinho"})
package app.gestaoIAWpp.ai;

import org.springframework.modulith.ApplicationModule;
