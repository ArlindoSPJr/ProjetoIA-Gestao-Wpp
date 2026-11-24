# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Estado do projeto

Projeto em fase inicial: uma demo do agente de atendimento está sendo construída (ver o plano abaixo). O produto planejado, as decisões já tomadas e as decisões em aberto estão em `docs/contexto.md`. **Leia esse arquivo antes de propor arquitetura ou funcionalidades.** Não trate itens listados como "Propostas" ou "Decisões em aberto" como decididos; pergunte ao usuário.

É um projeto de estudos (o objetivo principal é aprender) e também portfólio para vaga júnior de backend Java. Prefira explicar o porquê das escolhas a só entregar código.

## Fluxo de trabalho

- Feature nova, bug ou mudança de comportamento: use a skill **spec-flow**.
- Mudança trivial (texto, log, renomear): pode ir direto, mas os comandos da seção "Comandos" precisam passar.

## Testes

Siga `.claude/rules/testing.md`. Cobertura sobe cobrindo **caminhos diferentes** (partições, limites, branches, erros), nunca repetindo casos da mesma lógica.

## Limites de qualidade (fonte de verdade: Quality Gate do Sonar)

| Métrica | Limite | Onde é configurado |
|---|---|---|
| Issues novos | 0 | Sonar |
| Cobertura no código novo (linha e branch) | ≥ 80% | JaCoCo (`jacoco:check` no pom.xml) + Sonar |
| Duplicação no código novo | ≤ 3% | Sonar |
| Complexidade cognitiva por método | ≤ 15 | Sonar (java:S3776) |
| Complexidade ciclomática por método | ≤ 10 | Sonar (java:S1541) |
| Mutation score | ≥ 60% | PIT (`mutationThreshold` no pom.xml) |

## Dependências (validação obrigatória)

Antes de declarar qualquer dependência no `pom.xml`, confirme que ela existe e é a certa. Nomes inventados por IA podem ter sido registrados por atacantes ("slopsquatting").

1. **Existe no Maven Central** com `groupId:artifactId:version` exatos (falha se não existir):

   ```bash
   ./mvnw -B dependency:get -Dartifact=<groupId>:<artifactId>:<version> -Dtransitive=false
   ```

2. **É a dependência certa**: groupId de um publisher conhecido (ex.: `org.springframework`), repositório de código vinculado, uso relevante, release nos últimos 12 meses. Desconfie de groupId/artifactId quase iguais aos de projetos famosos.
3. **Versão fixa**: explícita, ou gerenciada pelo BOM do Spring Boot. Nada de `LATEST`, `RELEASE` ou faixas `[1.0,)`.
4. **Sem vulnerabilidades conhecidas** na versão escolhida (GitHub Advisory Database; a lista precisa voltar vazia):

   ```bash
   gh api -X GET advisories -f ecosystem=maven -f affects=<groupId>:<artifactId>@<version> --jq '.[] | .ghsa_id + " " + .severity'
   ```

   Depois do merge, o Dependabot (`.github/dependabot.yml`) vigia as dependências e abre PRs de atualização e de segurança.

5. Prefira os starters do Spring Boot (`spring-boot-starter-*`) a bibliotecas avulsas para a mesma função.
6. Registre cada dependência nova na seção "Dependências novas" da spec. Se qualquer item acima falhar, não adicione: pare e pergunte ao humano.

## Proibições

- Não baixar limites de cobertura, mutação ou Quality Gate, nem editar a configuração do Sonar, do JaCoCo ou do PIT (incluindo `excludes`).
- Não usar `// NOSONAR`, `@SuppressWarnings`, `@Disabled`, `@Ignore`, `assumeTrue(false)` ou `-DskipTests` para passar em checagem.
- Não apagar nem enfraquecer testes existentes. Se um teste parece errado, pare e pergunte.
- Não adicionar dependências fora da spec ou do plano.
- Não fazer merge, force push na branch principal ou alteração de segredos (`application-*.yml` com credenciais, variáveis de ambiente).
- Não alterar uma spec aprovada (`docs/specs/`). Divergência = perguntar ao humano.

## Comandos

Use o Maven Wrapper (`./mvnw` no Bash, `.\mvnw.cmd` no PowerShell):

```bash
./mvnw spring-boot:run                       # sobe a aplicação
./mvnw test                                  # testes unitários (*Test), sem banco e sem Docker
./mvnw test -Dtest=NomeDaClasseTest          # uma classe de teste
./mvnw test -Dtest=NomeDaClasseTest#metodo   # um método de teste
./mvnw clean package                         # gera o jar em target/
./mvnw clean verify                          # unitários + integração (*IT, Testcontainers, precisa do Docker) + cobertura (JaCoCo, falha abaixo de 80% de linha ou branch)
./mvnw test-compile org.pitest:pitest-maven:mutationCoverage   # mutação (PIT, falha abaixo de 60%)
```

O banco está ligado: PostgreSQL no Supabase, com URL, usuário e senha lidos do `.env` (`SUPABASE_DB_URL`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD`; ver `.env.example`). O Flyway aplica o schema de `src/main/resources/db/migration` e o seed da Loja Demo de `src/main/resources/db/demo` (as duas locations estão em `spring.flyway.locations`; tirar o seed de produção = remover a location `db/demo`). O Hibernate só valida o schema (`ddl-auto: validate`).

Testes: `./mvnw test` roda só os `*Test` (unitários, repositórios mockados, sem PostgreSQL e sem Docker). Os que precisam de banco terminam em `IT` e rodam no `./mvnw verify` (Failsafe), com `postgres:17` via Testcontainers (`TestcontainersConfiguration`), então o Docker precisa estar rodando. Nenhum teste usa chave da Anthropic (o `ChatModel` do Spring AI é mockado; nenhum teste chama a API real).

Demo do simulador (precisa de `ANTHROPIC_API_KEY`): copie `.env.example` para `.env` (ignorado pelo git) e preencha a chave, ou exporte a variável de ambiente. O Spring importa o `.env` via `spring.config.import` e `spring.ai.anthropic.api-key` lê `ANTHROPIC_API_KEY`.

```powershell
.\mvnw.cmd spring-boot:run   # depois abrir http://localhost:8080
```

Se o bot só responder "vou chamar um atendente", confira a ANTHROPIC_API_KEY e os logs (o `log.warn` do `AgenteSpringAi` mostra o erro).

Cada iteração do loop (chamada à API: finish reason, tokens, tools pedidas) é registrada no `RegistroTurno` pelo `LimiteIteracoesAdvisor` e volta em `AgenteResponse.iteracoes`; a página mostra isso na seção "Iterações do último turno". A resposta do simulador é `{texto, iteracoes}`. Para ver o JSON de requisição e resposta de cada rodada no terminal, troque o `SimpleLoggerAdvisor` para DEBUG em `logging.level` no `application.yaml`.

Modelo, effort e limite de iterações do agente ficam no bloco `agente:` do `application.yaml`. O `OutputConfig` (effort) vem do SDK da Anthropic que o Spring AI traz transitivamente; não há dependência direta de `anthropic-java` no `pom.xml`.

O limite do loop tem duas camadas: `agente.max-iteracoes` (principal, 5 chamadas à API por turno, aplicado pelo `LimiteIteracoesAdvisor`) e `spring.ai.tools.limits.max-total-tool-calls` (rede de segurança, 10 tools por turno, do próprio Spring AI). Estourar qualquer uma devolve a mensagem de fallback e restaura a memória ao estado de antes do turno.

Plano da demo: `docs/superpowers/plans/2026-11-20-demo-agente-atendimento.md` (spec em `docs/superpowers/specs/`).

## Estrutura da demo (pacotes sob `app.gestaoIAWpp`)

Cada classe fica no pacote do seu domínio (sem pacotes técnicos como `entity/` ou `service/`); testes no pacote espelho.

- `empresa/`: `NumeroWhatsapp` + `NumeroWhatsappRepository` (o número de atendimento, `phone_number_id`, aponta para a empresa) e `NumeroNaoCadastradoException`.
- `cliente/`: `Cliente`, `ClienteRepository` e `ClienteService` (identifica o cliente por empresa e `user_id`, criando-o com `INSERT ... ON CONFLICT DO NOTHING`).
- `produto/`: entidades do catálogo (`Produto`, `Opcao`, `ValorOpcao`, `Variante`: o que se vende é a variante, com preço e estoque próprios), `ProdutoRepository`, `VarianteRepository` (`daEmpresa`, e `baixarEstoque`/`devolverEstoque` como `UPDATE` nativo atômico), `ProdutoService` (busca por pontuação tolerante a acento e disponibilidade; a regra de negócio dos produtos mora aqui, não nas tools), exceções, records de domínio e `ProdutoTool` (as tools `consultar_produto` e `consultar_estoque`, só adaptador do `ProdutoService`); `dto/` com as respostas das tools.
- `carrinho/`: `Carrinho`, `ItemCarrinho`, `StatusCarrinho`, `CarrinhoRepository` (criação do carrinho `ABERTO` e upsert do item em SQL nativo com `ON CONFLICT`) e `CarrinhoService`: carrinho persistido no banco, no máximo um `ABERTO` por cliente; adicionar baixa o estoque e grava o item na mesma transação, remover devolve, o resumo usa o preço atual da variante e o reset da demo devolve o estoque e apaga os carrinhos abertos da empresa. `CarrinhoTool` (as tools do carrinho, só adaptador do `CarrinhoService`); `dto/` com as respostas das tools. As queries nativas de escrita limpam o contexto de persistência (`clearAutomatically`): leia de novo o que precisar depois delas.
- `ai/`: `AgenteAtendimento` (a porta da IA); `AgenteSpringAi` (monta o `ChatClient` com os advisors e roda o turno: memória por cliente, snapshot/restauração em caso de falha, fallback); `LimiteIteracoesAdvisor` (o `ToolCallingAdvisor` com teto de chamadas à API); `RegistroTurno` (iterações do turno); `ToolRegistry` (junta os `AgentTool` na ordem do `@Order`); `AgentTool` (interface marcadora das tools); `ContextoTool` (lê `empresaId` e `clienteId` do `ToolContext`); `dto/` (os `*Response`). Para criar uma tool: um `@Component` num pacote de domínio que implementa `AgentTool`, com `@Order`; prompt em `src/main/resources/prompts/sistema-atendimento.txt`. A memória de conversa de um cliente fica no `ChatMemory` compartilhado durante o turno; qualquer código futuro que limpe a memória de um cliente (handoff, `ENCERRADA`, expiração) precisa segurar o lock desse cliente (como o `responder` faz). O reset do simulador hoje não segura, o que é aceito na demo.
- `atendimento/`: `AtendimentoService.processarMensagem(phoneNumberId, userId, telefone, texto)`, entrada agnóstica de canal (o futuro webhook do WhatsApp chama o mesmo serviço): resolve número → empresa → cliente e chama o agente com `empresaId` e `clienteId`.
- `simulador/`: endpoints REST `/api/simulador/*` (`/mensagens`, `/estado` com o carrinho e as variantes, `/reset`) e a página `src/main/resources/static/index.html`. Usa o número `simulador.phone-number-id` do seed e `user_id = "BR.SIM" + telefone só com dígitos`.

Regra das camadas: as tools só delegam ao domínio (`ProdutoService`, `CarrinhoService`); nenhuma regra de negócio fica em `ProdutoTool`/`CarrinhoTool`. `empresaId` e `clienteId` vêm do `ToolContext` via `ContextoTool` (preenchido pelo `AgenteSpringAi`), nunca do modelo: o modelo só informa `varianteId`, termo e quantidade.

## Stack

- Java 21, Spring Boot 4.2.0 (starters separados no estilo do Boot 4: `spring-boot-starter-webmvc`, `spring-boot-starter-flyway` e os `*-test` correspondentes)
- Spring AI 2.1 (`spring-ai-starter-model-anthropic`): tools com `@Tool`, loop de tool calling pelo `ToolCallingAdvisor` (aqui estendido pelo `LimiteIteracoesAdvisor`) e histórico por cliente com `MessageChatMemoryAdvisor`. pgvector e o advisor de RAG ficam para a etapa de RAG.
- PostgreSQL (Supabase) + Spring Data JPA (Hibernate) + Flyway para o schema. As migrations vão em `src/main/resources/db/migration`; dados da demo em `src/main/resources/db/demo`. Toda tabela nova no `public` precisa de `ENABLE ROW LEVEL SECURITY` na mesma migration (o `RlsIT` falha se faltar); a `flyway_schema_history` é tratada pelo callback `afterMigrate.sql`.
- Testes de integração com Testcontainers (`postgres:17`), via `maven-failsafe-plugin`.
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
