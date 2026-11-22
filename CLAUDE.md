# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Estado do projeto

Projeto em fase inicial: uma demo do agente de atendimento está sendo construída (ver o plano abaixo). O produto planejado, as decisões já tomadas e as decisões em aberto estão em `docs/contexto.md`. **Leia esse arquivo antes de propor arquitetura ou funcionalidades.** Não trate itens listados como "Propostas" ou "Decisões em aberto" como decididos; pergunte ao usuário.

É um projeto de estudos (o objetivo principal é aprender) e também portfólio para vaga júnior de backend Java. Prefira explicar o porquê das escolhas a só entregar código.

## Comandos

Use o Maven Wrapper (`./mvnw` no Bash, `.\mvnw.cmd` no PowerShell):

```bash
./mvnw spring-boot:run                       # sobe a aplicação
./mvnw test                                  # roda todos os testes
./mvnw test -Dtest=NomeDaClasseTest          # uma classe de teste
./mvnw test -Dtest=NomeDaClasseTest#metodo   # um método de teste
./mvnw clean package                         # gera o jar em target/
```

O banco está desligado temporariamente via `spring.autoconfigure.exclude` (dados em memória); os testes rodam sem PostgreSQL e sem chave da Anthropic (o `ChatModel` do Spring AI é mockado nos testes; nenhum teste chama a API real).

Demo do simulador (precisa de `ANTHROPIC_API_KEY`): copie `.env.example` para `.env` (ignorado pelo git) e preencha a chave, ou exporte a variável de ambiente. O Spring importa o `.env` via `spring.config.import` e `spring.ai.anthropic.api-key` lê `ANTHROPIC_API_KEY`.

```powershell
.\mvnw.cmd spring-boot:run   # depois abrir http://localhost:8080
```

Se o bot só responder "vou chamar um atendente", confira a ANTHROPIC_API_KEY e os logs (o `log.warn` do `AgenteSpringAi` mostra o erro).

Cada iteração do loop (chamada à API: finish reason, tokens, tools pedidas) é registrada no `RegistroTurno` pelo `LimiteIteracoesAdvisor` e volta em `RespostaAgente.iteracoes`; a página mostra isso na seção "Iterações do último turno". A resposta do simulador é `{texto, iteracoes}`. Para ver o JSON de requisição e resposta de cada rodada no terminal, troque o `SimpleLoggerAdvisor` para DEBUG em `logging.level` no `application.yaml`.

Modelo, effort e limite de iterações do agente ficam no bloco `agente:` do `application.yaml`. O `OutputConfig` (effort) vem do SDK da Anthropic que o Spring AI traz transitivamente; não há dependência direta de `anthropic-java` no `pom.xml`.

O limite do loop tem duas camadas: `agente.max-iteracoes` (principal, 5 chamadas à API por turno, aplicado pelo `LimiteIteracoesAdvisor`) e `spring.ai.tools.limits.max-total-tool-calls` (rede de segurança, 10 tools por turno, do próprio Spring AI). Estourar qualquer uma devolve a mensagem de fallback e restaura a memória ao estado de antes do turno.

Plano da demo: `docs/superpowers/plans/2026-11-20-demo-agente-atendimento.md` (spec em `docs/superpowers/specs/`).

## Estrutura da demo (pacotes sob `app.gestaoIAWpp`)

- `produto/`: `CatalogoMock` (8 produtos e estoque inicial em memória), `ProdutoRepository` + `ProdutoRepositoryEmMemoria` (busca por pontuação, tolerante a acento e pontuação), `ProdutoService` (busca e disponibilidade; a regra de negócio dos produtos mora aqui, não nas tools), `EstoqueService` (baixa/devolução atômicas).
- `carrinho/`: `CarrinhoService`, um carrinho por telefone; adicionar baixa o estoque, remover devolve.
- `agente/`: `AgenteAtendimento` (a porta da IA); `AgenteSpringAi` (monta o `ChatClient` com os advisors e roda o turno: memória por telefone, snapshot/restauração em caso de falha, fallback); `LimiteIteracoesAdvisor` (o `ToolCallingAdvisor` com teto de chamadas à API); `RegistroTurno` (iterações do turno); `FerramentasAgente` (as 5 tools com `@Tool`, só adaptador); prompt em `src/main/resources/prompts/sistema-atendimento.txt`. A memória de conversa de um telefone fica no `ChatMemory` compartilhado durante o turno; qualquer código futuro que limpe a memória de um telefone (handoff, `ENCERRADA`, expiração) precisa segurar o lock desse telefone (como o `responder` faz). O reset do simulador hoje não segura, o que é aceito na demo.
- `atendimento/`: `AtendimentoService.processarMensagem(telefone, texto)`, entrada agnóstica de canal (o futuro webhook do WhatsApp chama o mesmo serviço).
- `simulador/`: endpoints REST `/api/simulador/*` e a página `src/main/resources/static/index.html`.

Regra das camadas: as tools só delegam ao domínio (`ProdutoService`, `CarrinhoService`); nenhuma regra de negócio fica em `FerramentasAgente`. O telefone vem do `ToolContext` (preenchido pelo `AgenteSpringAi`), nunca do modelo: o modelo só informa ids, termo e quantidade.

## Stack

- Java 21, Spring Boot 4.2.0 (starters separados no estilo do Boot 4: `spring-boot-starter-webmvc`, `spring-boot-starter-flyway` e os `*-test` correspondentes)
- Spring AI 2.1 (`spring-ai-starter-model-anthropic`): tools com `@Tool`, loop de tool calling pelo `ToolCallingAdvisor` (aqui estendido pelo `LimiteIteracoesAdvisor`) e histórico por telefone com `MessageChatMemoryAdvisor`. pgvector e o advisor de RAG ficam para a etapa de RAG.
- PostgreSQL + Flyway para o schema. As migrations vão em `src/main/resources/db/migration`.
- Pacote base: `app.gestaoIAWpp`

## Arquitetura pretendida (de `docs/contexto.md`)

O sistema tem três camadas: chatbot (interface de conversa), agente de IA (executa ações via tools) e sistema de gestão (fonte da verdade sobre clientes, pedidos, inbox do atendente e handoff). Regras que valem para qualquer código novo:

- **O sistema de gestão precisa funcionar sem a IA.** A IA fica isolada atrás de uma interface (ex.: `AgenteAtendimento`), num monolito modular.
- Tudo em Java/Spring. Não criar serviço separado em Python.
- Preço e estoque sempre vêm de tools ou do banco, nunca do texto gerado pelo modelo. O carrinho é persistido no banco e manipulado via tools, não guardado só na memória da LLM.
- O bot nunca coleta dados de cartão, só a preferência de forma de pagamento. Um atendente humano valida o pedido inteiro antes do pagamento.
- Usar só a WhatsApp Cloud API oficial (nada de Baileys ou Evolution API). Pela política da Meta, o bot deve se limitar ao escopo da loja.
- O framework de IA é o Spring AI; não adicionar LangChain4j (só um dos dois).

O vocabulário de domínio é em português (pedido, carrinho, atendente, estados como `AGUARDANDO_VALIDACAO`). Mantenha esses nomes no código.
