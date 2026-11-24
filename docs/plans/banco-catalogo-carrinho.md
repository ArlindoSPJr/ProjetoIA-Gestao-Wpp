# Banco etapa 1 (catálogo com variações e carrinho) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Trocar o catálogo, o estoque e o carrinho em memória por PostgreSQL (Supabase), no modelo multiempresa com variantes, mantendo o simulador funcionando.

**Architecture:** Flyway cria 10 tabelas e um seed da Loja Demo; Spring Data JPA mapeia entidades por domínio (`empresa/`, `cliente/`, `produto/`, `carrinho/`), com SQL nativo nas operações atômicas (baixa de estoque, criação do carrinho aberto, upsert de item, criação de cliente). O `AtendimentoService` resolve número → empresa → cliente e o agente recebe `empresaId` e `clienteId` pelo `ToolContext`.

**Tech Stack:** Java 21, Spring Boot 4.2.0, Spring Data JPA (Hibernate 7.4), Flyway 12, PostgreSQL 17, Spring AI 2.1, JUnit 5, Mockito, Testcontainers 2.0.5.

**Spec:** `docs/specs/2026-11-24-banco-catalogo-carrinho.md` (aprovada). Leia-a inteira antes da primeira tarefa: RN, CA e a matriz T01–T28 vêm de lá.

## Global Constraints

- Branch `feat/banco-catalogo-carrinho`, criada da `main` atualizada (`git pull`), que já tem o `spring.datasource` do PR #9.
- Nenhuma dependência além das quatro da spec: `spring-boot-testcontainers`, `org.testcontainers:testcontainers-postgresql`, `org.testcontainers:testcontainers-junit-jupiter` (todas `test`, versão do BOM do Boot) e o `maven-failsafe-plugin` (versão do parent).
- Cada classe no pacote do seu domínio (seção "Organização por domínio" da spec). DTOs de tool em `produto/dto/` e `carrinho/dto/`; `ai/dto/` só com `AgenteResponse`, `IteracaoTurnoResponse`, `ToolPedidaResponse`. Testes no pacote espelho.
- Os 5 nomes de tool não mudam: `consultar_produto`, `consultar_estoque`, `adicionar_ao_carrinho`, `remover_do_carrinho`, `ver_carrinho`.
- `empresaId` e `clienteId` só pelo `ToolContext`, chaves `"empresaId"` e `"clienteId"` (`ContextoTool.CHAVE_EMPRESA_ID`, `ContextoTool.CHAVE_CLIENTE_ID`). Nunca como parâmetro de tool.
- `./mvnw test` roda sem Docker e sem banco (só `*Test`). Testes que precisam de PostgreSQL terminam em `IT` e rodam no `./mvnw verify` (Failsafe), com `postgres:17` via Testcontainers.
- Nome de teste `deve<Resultado>_quando<Condição>`, com `@DisplayName("Txx ...")` citando o ID da matriz. Mock só nas fronteiras (repositórios, agente).
- Mensagens exatas: `"Quantidade deve ser maior que zero."`, `"Telefone e texto são obrigatórios."`, `"Número de atendimento não cadastrado."`, `"Variante <id> não encontrada."`, `"Variante <id> não está no carrinho."`, `"Estoque insuficiente para <nome>: solicitado <q>, disponível <d>."`, `"Telefone inválido: use só números com DDI, ex.: 5511999990000."`.
- Seed: `phone_number_id = 'simulador-loja-demo'`, igual a `simulador.phone-number-id`. No simulador, `user_id = "BR.SIM" + <telefone só com dígitos>`.
- Commits em português, terminando com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Ao fim de cada tarefa, `./mvnw test` e `./mvnw verify` passam.

## Review Focus

- Telefone digitado com formatação no simulador (`+55 (11) 99999-0000`): vira só dígitos e funciona; com menos de 10 ou mais de 15 dígitos, 400 com a mensagem de telefone inválido (nunca 500 por CHECK). Teste na Task 3.
- `varianteId` que não é UUID (`"P001"`, `""`) nas tools: resposta "Variante <id> não encontrada.", nunca erro de conversão. Teste na Task 4.
- Leitura logo depois do `UPDATE` nativo na mesma transação (contexto de persistência desatualizado): após `adicionar`, o resumo e a disponibilidade mostram o estoque novo. Teste na Task 5.
- Busca com N+1: carregar o catálogo e percorrer variantes, valores e opções usa no máximo 4 statements. Teste na Task 4.
- Cliente sem carrinho aberto: `ver` devolve lista vazia e total `0.00`; `remover` lança "não está no carrinho", não `NullPointerException`. Teste na Task 5.

---

### Task 1: Banco ligado, schema, seed e testes de integração

| Campo | Valor |
|---|---|
| ID | T1 (Fase 1) |
| CA/RN | CA01, CA02, CA14, CA16, RN13 |
| Arquivos permitidos | `pom.xml`, `application.yaml`, `db/migration/**`, `db/demo/**`, `src/test/java/app/gestaoIAWpp/TestcontainersConfiguration.java`, `GestaoIaWppApplicationIT.java` (renomeado do `GestaoIaWppApplicationTests.java`), `produto/SeedCatalogoIT.java` |
| Arquivos proibidos | todo `src/main/java/**` |
| Dependências | nenhuma |
| Tipo | SEQUENCIAL |
| Tamanho | PEQUENA |

Atende: CA01, CA02, CA14, CA16, RN13, D4, D7, D11. Matriz: T01, T02.

**Files:**
- Modify: `pom.xml` (3 dependências `test` + `maven-failsafe-plugin` com goals `integration-test` e `verify`; no `pitest-maven`, `<excludedTestClasses><param>app.gestaoIAWpp.**.*IT</param></excludedTestClasses>`)
- Modify: `src/main/resources/application.yaml`
- Create: `src/main/resources/db/migration/V1__catalogo_carrinho.sql`
- Create: `src/main/resources/db/demo/V2__seed_loja_demo.sql`
- Create: `src/test/java/app/gestaoIAWpp/TestcontainersConfiguration.java`
- Move: `src/test/java/app/gestaoIAWpp/GestaoIaWppApplicationTests.java` → `GestaoIaWppApplicationIT.java`
- Create: `src/test/java/app/gestaoIAWpp/produto/SeedCatalogoIT.java`

**Interfaces:**
- Produces: `TestcontainersConfiguration` (`@TestConfiguration(proxyBeanMethods = false)` com `@Bean @ServiceConnection` de um `PostgreSQLContainer` `postgres:17`, do pacote do módulo `testcontainers-postgresql` 2.x). Todo `*IT` faz `@Import(TestcontainersConfiguration.class)`; os `@DataJpaTest` usam também `@AutoConfigureTestDatabase(replace = NONE)`.
- Produces: tabelas e seed abaixo, usados por todas as tarefas.

- [ ] **Step 0: Validar as dependências** pela seção "Dependências" do CLAUDE.md antes de declará-las: `./mvnw -B dependency:get -Dartifact=org.testcontainers:testcontainers-postgresql:2.0.5 -Dtransitive=false` (idem `testcontainers-junit-jupiter:2.0.5` e `org.springframework.boot:spring-boot-testcontainers:4.2.0`) e `gh api -X GET advisories -f ecosystem=maven -f affects=<g>:<a>@<v>` vazio para cada uma. Falhou: pare e pergunte.

- [ ] **Step 1: `application.yaml`.** Remover o bloco `spring.autoconfigure.exclude` e o comentário dele. Adicionar `spring.jpa.hibernate.ddl-auto: validate`, `spring.jpa.open-in-view: false`, `spring.flyway.locations: classpath:db/migration, classpath:db/demo` e, na raiz, `simulador.phone-number-id: simulador-loja-demo`.

- [ ] **Step 2: `V1__catalogo_carrinho.sql`.** Ids `uuid PRIMARY KEY DEFAULT gen_random_uuid()`; datas `timestamptz NOT NULL DEFAULT now()`; `ativo boolean NOT NULL DEFAULT true`. Restrições que a spec fixa:
  - `empresa`: `razao_social`, `nome_fantasia` `NOT NULL`; `documento text NOT NULL UNIQUE CHECK (documento ~ '^([0-9]{11}|[0-9]{14})$')`; `segmento`, `email`, `telefone_contato` opcionais; `criado_em`, `atualizado_em`.
  - `numero_whatsapp`: `empresa_id NOT NULL REFERENCES empresa`; `phone_number_id text NOT NULL UNIQUE`; `waba_id`, `business_id`, `numero_exibicao`, `apelido` opcionais; `ativo`, `ativado_em` (default now()), `desativado_em` (nulo), `criado_em`.
  - `produto`: `empresa_id NOT NULL REFERENCES empresa`, `nome NOT NULL`, `descricao`, `categoria`, `ativo`, `criado_em`, `atualizado_em`; índice em `empresa_id`.
  - `opcao`: `produto_id NOT NULL REFERENCES produto ON DELETE CASCADE`, `nome NOT NULL`, `posicao int NOT NULL DEFAULT 0`, `UNIQUE (produto_id, nome)`.
  - `valor_opcao`: `opcao_id NOT NULL REFERENCES opcao ON DELETE CASCADE`, `valor NOT NULL`, `posicao int NOT NULL DEFAULT 0`, `UNIQUE (opcao_id, valor)`.
  - `variante`: `produto_id NOT NULL REFERENCES produto ON DELETE CASCADE`, `sku text` (sem índice), `preco numeric(12,2) NOT NULL CHECK (preco > 0)`, `quantidade_estoque int NOT NULL CHECK (quantidade_estoque >= 0)`, `ativo`, `criado_em`, `atualizado_em`.
  - `variante_valor`: `variante_id REFERENCES variante ON DELETE CASCADE`, `valor_opcao_id REFERENCES valor_opcao ON DELETE RESTRICT`, `PRIMARY KEY (variante_id, valor_opcao_id)`.
  - `cliente`: `empresa_id NOT NULL REFERENCES empresa`, `user_id text NOT NULL`, `user_id_anterior`, `telefone text CHECK (telefone ~ '^[0-9]{10,15}$')`, `nome_perfil`, `username`, `nome`, `email`, `aceita_marketing_em timestamptz`, `criado_em`, `atualizado_em`, `UNIQUE (empresa_id, user_id)`.
  - `carrinho`: `empresa_id NOT NULL REFERENCES empresa`, `cliente_id NOT NULL REFERENCES cliente`, `status text NOT NULL CHECK (status IN ('ABERTO','CONVERTIDO','ABANDONADO'))`, `criado_em`, `atualizado_em`; `CREATE UNIQUE INDEX ux_carrinho_aberto_por_cliente ON carrinho (cliente_id) WHERE status = 'ABERTO'`.
  - `item_carrinho`: `carrinho_id NOT NULL REFERENCES carrinho ON DELETE CASCADE`, `variante_id NOT NULL REFERENCES variante ON DELETE RESTRICT`, `quantidade int NOT NULL CHECK (quantidade > 0)`, `adicionado_em`, `UNIQUE (carrinho_id, variante_id)`.

- [ ] **Step 3: `V2__seed_loja_demo.sql`.** Empresa `razao_social 'Loja Demo LTDA'`, `nome_fantasia 'Loja Demo'`, `documento '11222333000181'`, `segmento 'vestuário'`. Número `phone_number_id 'simulador-loja-demo'`, `numero_exibicao '5511900000000'`, `apelido 'Vendas'`. Produtos (nome · descrição · categoria · opções em ordem · variantes com preço e estoque):
  - `Camisa Básica` · "Camisa de algodão, gola redonda" · camisa · Cor (Preta, Branca), Tamanho (M, G) · Preta/M 49.90 12; Preta/G 49.90 0; Branca/M 49.90 8
  - `Camisa Polo` · "Camisa polo de piquet" · camisa · Cor (Azul), Tamanho (M) · Azul/M 79.90 5
  - `Calça Jeans Masculina` · "Calça jeans reta, lavagem escura" · calça · Tamanho (42) · 42: 149.90 3
  - `Calça Jeans Feminina` · "Calça jeans skinny, lavagem clara" · calça · Tamanho (38) · 38: 139.90 6
  - `Tênis Casual` · "Tênis de couro sintético" · tênis · Cor (Branco), Tamanho (40) · Branco/40 199.90 4
  - `Boné Ajustável` · "Boné preto de aba curva com regulagem" · acessório · sem opções · variante padrão 39.90 15

- [ ] **Step 4: Escrever `GestaoIaWppApplicationIT` (T01).** Mantém os dois testes atuais (`contextLoads`, `springAiConfigurado`) e acrescenta `deveAplicarSchemaESeed_quandoAplicacaoSobe` com `JdbcTemplate`: 1 empresa; 1 `numero_whatsapp` ativo com `phone_number_id` igual ao valor de `simulador.phone-number-id`; 6 produtos; 8 variantes; e o mapa `nome do produto + valores` → estoque igual ao do Step 3.

- [ ] **Step 5: Escrever `SeedCatalogoIT` (T02).** `@DataJpaTest` com `JdbcTemplate`. Três consultas que devem devolver 0 linhas: (a) par (variante, opção do produto dela) com contagem de valores daquela opção ≠ 1; (b) `variante_valor` cujo valor pertence a opção de outro produto; (c) variante de produto com opções e sem nenhuma linha em `variante_valor`.

- [ ] **Step 6: Rodar.** `./mvnw test` → passa sem Docker (o `*IT` não roda). `./mvnw verify` → T01 e T02 passam e o `jacoco:check` (80% linha e branch) continua passando; o `jacoco.exec` recebe também os `*IT` (o `argLine` do `prepare-agent` vale para o Failsafe).

- [ ] **Step 7: Commit** `feat: liga o PostgreSQL com Flyway, schema do catálogo e carrinho e seed da Loja Demo`.

---

### Task 2: Número de atendimento e cliente

| Campo | Valor |
|---|---|
| ID | T2 (Fase 2) |
| CA/RN | RN11, RN12, RN15, CA11 |
| Arquivos permitidos | `src/main/java/app/gestaoIAWpp/empresa/**`, `src/main/java/app/gestaoIAWpp/cliente/**`, `src/test/java/app/gestaoIAWpp/cliente/**` |
| Arquivos proibidos | `ai/**`, `produto/**`, `carrinho/**`, `atendimento/**`, `simulador/**`, `pom.xml`, migrations |
| Dependências | T1 |
| Tipo | SEQUENCIAL |
| Tamanho | PEQUENA |

Atende: RN11, RN12 (repositório), RN15, CA11 (persistência). Matriz: T08, T28.

**Files:**
- Create: `src/main/java/app/gestaoIAWpp/empresa/NumeroWhatsapp.java`, `NumeroWhatsappRepository.java`, `NumeroNaoCadastradoException.java`
- Create: `src/main/java/app/gestaoIAWpp/cliente/Cliente.java`, `ClienteRepository.java`, `ClienteService.java`
- Test: `src/test/java/app/gestaoIAWpp/cliente/ClienteServiceIT.java`

**Interfaces:**
- Produces: `@Entity NumeroWhatsapp` (mapeia só `id UUID`, `empresaId UUID`, `phoneNumberId String`, `ativo boolean`); `NumeroWhatsappRepository extends JpaRepository<NumeroWhatsapp, UUID>` com `Optional<NumeroWhatsapp> findByPhoneNumberIdAndAtivoTrue(String phoneNumberId)`.
- Produces: `NumeroNaoCadastradoException extends IllegalArgumentException`, mensagem `"Número de atendimento não cadastrado."`.
- Produces: `@Entity Cliente` (`id`, `empresaId`, `userId`, `telefone`); `ClienteRepository extends JpaRepository<Cliente, UUID>` com `@Modifying @Query(nativeQuery = true) int inserirSeNaoExiste(UUID empresaId, String userId, String telefone)` (`INSERT ... ON CONFLICT (empresa_id, user_id) DO NOTHING`) e `Optional<Cliente> findByEmpresaIdAndUserId(UUID empresaId, String userId)`.
- Produces: `ClienteService` com `@Transactional Cliente identificar(UUID empresaId, String userId, String telefone)` (insere se não existe, depois busca) e `Optional<Cliente> buscar(UUID empresaId, String userId)`.

- [ ] **Step 1: Escrever `ClienteServiceIT`** (`@DataJpaTest`, `@Import({TestcontainersConfiguration.class, ClienteService.class})`, empresa do seed):
  - T08 `deveCriarEReaproveitarCliente_quandoIdentificaDuasVezes`: `identificar(empresa, "BR.SIM5511999990000", "5511999990000")` duas vezes → mesmo `id`; 1 linha em `cliente` com esse `user_id`.
  - T28 `deveRecusarTelefoneForaDoFormato_quandoInsereCliente`: parametrizado; `"+5511999990000"`, `"123456789"` (9), `"1234567890123456"` (16) → `DataIntegrityViolationException`; `"1234567890"` (10) e `"123456789012345"` (15) → aceito.
  - T28 `deveRecusarDocumentoForaDoFormato_quandoInsereEmpresa`: `JdbcTemplate` insere empresa com documento de 13 dígitos → `DataIntegrityViolationException`; com 11 → aceito.
- [ ] **Step 2: Rodar `./mvnw verify -Dit.test=ClienteServiceIT`** → falha por classes inexistentes.
- [ ] **Step 3: Implementar as classes das Interfaces.**
- [ ] **Step 4: `./mvnw verify`** → passa.
- [ ] **Step 5: Commit** `feat: número de atendimento e cliente identificado por empresa e user_id`.

---

### Task 3: Contexto do atendimento (empresa e cliente no lugar do telefone)

| Campo | Valor |
|---|---|
| ID | T3 (Fase 3) |
| CA/RN | RN06, RN11, RN12, CA11, CA12 |
| Arquivos permitidos | `ai/ContextoTool.java`, `ai/AgenteAtendimento.java`, `ai/AgenteSpringAi.java`, `atendimento/**`, `simulador/SimuladorController.java`, `carrinho/CarrinhoTool.java` e os testes listados em Files |
| Arquivos proibidos | `produto/**` (exceto ler), `empresa/**`, `cliente/**` (só consumir), `pom.xml`, migrations |
| Dependências | T2 |
| Tipo | SEQUENCIAL |
| Tamanho | PEQUENA |

Atende: RN06 (contexto), RN11, RN12, CA11, CA12, D9. Matriz: T22, T23, T24, T26.

**Files:**
- Modify: `ai/ContextoTool.java`, `ai/AgenteAtendimento.java`, `ai/AgenteSpringAi.java`
- Modify: `atendimento/AtendimentoService.java`, `simulador/SimuladorController.java`
- Modify: `carrinho/CarrinhoTool.java` (passa `ContextoTool.clienteId(ctx).toString()` ao `CarrinhoService` atual, que continua com chave `String` até a Task 4)
- Test: `ai/ContextoToolTest.java`, `ai/AgenteSpringAiTest.java`, `atendimento/AtendimentoServiceTest.java`, `simulador/SimuladorControllerTest.java`, `ai/CaracterizacaoToolsTest.java`, `ai/ToolsAgenteTest.java` (contexto com as chaves novas)

**Interfaces:**
- Consumes: `NumeroWhatsappRepository`, `NumeroNaoCadastradoException`, `ClienteService` (Task 2).
- Produces: `ContextoTool.CHAVE_EMPRESA_ID = "empresaId"`, `CHAVE_CLIENTE_ID = "clienteId"`, `static UUID empresaId(ToolContext)`, `static UUID clienteId(ToolContext)`; ausência → `IllegalStateException("Empresa ausente no contexto da ferramenta.")` / `("Cliente ausente no contexto da ferramenta.")`. Sai `telefone(ToolContext)` e `CHAVE_TELEFONE`.
- Produces: `AgenteAtendimento.responder(UUID empresaId, UUID clienteId, String mensagem)`; `reiniciarConversas()` igual.
- Produces: `AtendimentoService(NumeroWhatsappRepository, ClienteService, AgenteAtendimento)` com `AgenteResponse processarMensagem(String phoneNumberId, String userId, String telefone, String texto)` e `UUID empresaDoNumero(String phoneNumberId)`.
- Produces: `SimuladorController` com `@Value("${simulador.phone-number-id}")`; `static String userIdDoSimulador(String telefone)` (package-private): `null` se em branco; só dígitos; fora de 10–15 dígitos → `IllegalArgumentException` com a mensagem de telefone inválido; senão `"BR.SIM" + dígitos`.

- [ ] **Step 1: Testes que falham.**
  - T24 `ContextoToolTest`: contexto com as duas chaves → devolve os UUIDs; sem `empresaId` → `IllegalStateException` com a mensagem; sem `clienteId` → idem.
  - T22 `AtendimentoServiceTest.deveRecusarSemChamarAgente_quandoNumeroNaoCadastradoOuInativo`: `findByPhoneNumberIdAndAtivoTrue` vazio → `NumeroNaoCadastradoException`; `verifyNoInteractions(agente, clienteService)`.
  - T23 `deveIdentificarClienteEPassarIdsAoAgente_quandoNumeroValido`: número da empresa E → `clienteService.identificar(E, "BR.SIM5511999990000", "5511999990000")` → cliente C → `agente.responder(E, C, "oi")` com o texto sem espaços nas pontas.
  - Mantém o caso atual: `userId` ou texto em branco → `IllegalArgumentException("Telefone e texto são obrigatórios.")` sem consultar o repositório.
  - T26 `AgenteSpringAiTest`: dois `clienteId` diferentes → memórias separadas; o `ToolContext` recebido pela tool tem `empresaId` e `clienteId`; a chave de memória é `clienteId.toString()`.
  - `SimuladorControllerTest`: `POST /mensagens {"telefone":"+55 (11) 99999-0000","texto":"oi"}` → `processarMensagem("simulador-loja-demo", "BR.SIM5511999990000", "5511999990000", "oi")`; telefone `"123"` → 400 com a mensagem de telefone inválido; telefone em branco → 400 `"Telefone e texto são obrigatórios."`; `NumeroNaoCadastradoException` → 400 com a mensagem dela.
- [ ] **Step 2: `./mvnw test`** → falham.
- [ ] **Step 3: Implementar.** `AgenteSpringAi`: lock e memória por `clienteId`; `toolContext(Map.of(CHAVE_EMPRESA_ID, empresaId, CHAVE_CLIENTE_ID, clienteId, RegistroTurno.CHAVE, registro))`. `SimuladorController.estado(telefone)`: `empresaDoNumero` + `clienteService.buscar(...)`; sem cliente → carrinho vazio. `/reset` sem mudança nesta tarefa.
- [ ] **Step 4: `./mvnw test` e `./mvnw verify`** → passam.
- [ ] **Step 5: Commit** `refactor: atendimento resolve empresa e cliente e o agente usa os ids no contexto`.

---

### Task 4: Catálogo e estoque no banco

| Campo | Valor |
|---|---|
| ID | T4 (Fase 4) |
| CA/RN | RN01, RN06, RN07, RN09, RN10, CA03, CA04, CA10, CA15, CA17 |
| Arquivos permitidos | `produto/**`, `carrinho/**` (exceto entidades novas da T5), `ai/dto/**` (só remover/mover), `simulador/**`, `prompts/sistema-atendimento.txt`, `src/test/resources/caracterizacao-tools/**`, `docs/contexto.md` e os testes listados em Files |
| Arquivos proibidos | `pom.xml`, migrations, `empresa/**`, `cliente/**`, `ai/AgenteSpringAi.java` |
| Dependências | T3 |
| Tipo | SEQUENCIAL |
| Tamanho | GRANDE |

Atende: RN01, RN06, RN07, RN09, RN10, CA03, CA04, CA10, CA15, CA17 (estoque), D5, D10. Matriz: T03, T04, T09, T10, T11–T16, T20, T25.

**Files:**
- Create: `produto/Produto.java` (entidade; substitui o record), `Opcao.java`, `ValorOpcao.java`, `Variante.java`, `VarianteRepository.java`, `VarianteNaoEncontradaException.java`, `VarianteResumo.java`, `DisponibilidadeVariante.java`
- Replace: `produto/ProdutoRepository.java` (passa a `JpaRepository<Produto, UUID>`), `produto/ProdutoService.java`, `produto/ProdutoResumo.java`, `produto/EstoqueInsuficienteException.java`
- Move + change: `ai/dto/ConsultarProdutoResponse.java`, `ProdutoResponse.java`, `ConsultarEstoqueResponse.java` → `produto/dto/`; create `produto/dto/VarianteResponse.java`
- Move + change: `ai/dto/CarrinhoResponse.java`, `ItemCarrinhoResponse.java` → `carrinho/dto/`
- Modify: `produto/ProdutoTool.java`, `carrinho/CarrinhoTool.java`, `carrinho/CarrinhoService.java` (ainda em memória), `carrinho/CarrinhoResumo.java`, `carrinho/ItemNaoEstaNoCarrinhoException.java`; rename `carrinho/ItemCarrinho.java` (record) → `carrinho/ItemCarrinhoResumo.java`
- Modify: `simulador/SimuladorController.java`, `simulador/ItemEstoqueView.java`, `src/main/resources/prompts/sistema-atendimento.txt`, `src/test/resources/caracterizacao-tools/*.json`, `docs/contexto.md` (decisão em aberto 2 vira decidida: variações normalizadas com estoque por variante)
- Delete: `produto/CatalogoMock.java`, `ProdutoRepositoryEmMemoria.java`, `EstoqueService.java`, `ProdutoNaoEncontradoException.java`, `ProdutoEncontrado.java`, `Produto` record; testes `EstoqueServiceTest`, `ProdutoRepositoryEmMemoriaTest`
- Test: `produto/VarianteRepositoryIT.java`, `produto/ProdutoRepositoryIT.java`, `produto/ProdutoServiceTest.java` (reescrito), `ai/CaracterizacaoToolsTest.java`, `carrinho/CarrinhoServiceTest.java` (adaptado à assinatura nova)

**Interfaces:**
- Consumes: `ContextoTool.empresaId/clienteId` (Task 3).
- Produces entidades: `Produto` (`id`, `empresaId`, `nome`, `descricao`, `categoria`, `ativo`, `Set<Opcao> opcoes`, `Set<Variante> variantes`); `Opcao` (`id`, `produto`, `nome`, `posicao`, `Set<ValorOpcao> valores`); `ValorOpcao` (`id`, `opcao`, `valor`, `posicao`); `Variante` (`id`, `produto`, `sku`, `BigDecimal preco`, `int quantidadeEstoque`, `ativo`, `@ManyToMany @JoinTable(name = "variante_valor") Set<ValorOpcao> valores`), com `String opcoesDescritas()` (RN10; `null` sem valores; ordem por `opcao.posicao`) e `String nomeCompleto()` (`"Camisa Básica (Preta / M)"` ou só o nome do produto). Coleções são `Set` (evita `MultipleBagFetchException`).
- Produces: `ProdutoRepository.catalogoAtivo(UUID empresaId): List<Produto>` (produtos ativos da empresa com opções, valores e variantes carregados, sem N+1; o filtro de variante ativa é feito no service).
- Produces: `VarianteRepository extends JpaRepository<Variante, UUID>` com `Optional<Variante> daEmpresa(UUID varianteId, UUID empresaId)` (variante e produto ativos, produto da empresa, valores e opções carregados), `@Modifying(clearAutomatically = true, flushAutomatically = true) @Query(nativeQuery = true) int baixarEstoque(UUID varianteId, int quantidade)` (`UPDATE variante SET quantidade_estoque = quantidade_estoque - :q, atualizado_em = now() WHERE id = :id AND quantidade_estoque >= :q`) e `int devolverEstoque(UUID varianteId, int quantidade)` (mesmas anotações).
- Produces records de domínio: `VarianteResumo(UUID varianteId, String nome, String opcoes, BigDecimal preco, boolean emEstoque, int quantidadeDisponivel)`; `ProdutoResumo(UUID produtoId, String nome, String descricao, List<VarianteResumo> variantes)`; `ResultadoBusca(List<ProdutoResumo> produtos, int totalEncontrado, String mensagem)` (igual a hoje); `DisponibilidadeVariante(UUID varianteId, String nome, String opcoes, boolean emEstoque, int quantidadeDisponivel)`.
- Produces: `ProdutoService(ProdutoRepository, VarianteRepository)` com `ResultadoBusca buscar(UUID empresaId, String termo)`, `DisponibilidadeVariante consultarDisponibilidade(UUID empresaId, UUID varianteId)`, `List<VarianteResumo> listarVariantes(UUID empresaId)` (ordem: nome do produto, depois `opcoes`) e `Variante varianteDaEmpresa(UUID empresaId, UUID varianteId)` (lança `VarianteNaoEncontradaException`).
- Produces: `VarianteNaoEncontradaException(String varianteId)`, `"Variante <id> não encontrada."`; `EstoqueInsuficienteException(UUID varianteId, String nome, int solicitado, int disponivel)`, `"Estoque insuficiente para <nome>: solicitado <q>, disponível <d>."`; `ItemNaoEstaNoCarrinhoException(UUID varianteId)`, `"Variante <id> não está no carrinho."`.
- Produces DTOs: `produto/dto/ConsultarProdutoResponse(List<ProdutoResponse> produtos, int totalEncontrado, @JsonInclude(NON_NULL) String mensagem)`; `ProdutoResponse(UUID produtoId, String nome, String descricao, List<VarianteResponse> variantes)`; `VarianteResponse(UUID varianteId, @JsonInclude(NON_NULL) String opcoes, BigDecimal preco, boolean emEstoque, int quantidadeDisponivel)`; `ConsultarEstoqueResponse(UUID varianteId, String nome, @JsonInclude(NON_NULL) String opcoes, boolean emEstoque, int quantidadeDisponivel)`; `carrinho/dto/CarrinhoResponse(List<ItemCarrinhoResponse> itens, BigDecimal total)`; `ItemCarrinhoResponse(UUID varianteId, String nome, BigDecimal precoUnitario, int quantidade, BigDecimal subtotal)`.
- Produces: `ProdutoTool.DESCRICAO_VARIANTE_ID = "Id da variante retornado por consultar_produto."` e `static UUID idDeVariante(String)` (texto que não é UUID → `VarianteNaoEncontradaException` com o texto recebido). Parâmetros das tools: `consultar_produto(termo)`, `consultar_estoque(varianteId)`, `adicionar_ao_carrinho(varianteId, quantidade)`, `remover_do_carrinho(varianteId, quantidade?)`, `ver_carrinho()`.
- Produces: `CarrinhoService` com a assinatura final (implementação ainda em memória, `Map<UUID clienteId, ...>`, estoque via `VarianteRepository`): `CarrinhoResumo adicionar(UUID empresaId, UUID clienteId, UUID varianteId, int quantidade)`, `CarrinhoResumo remover(UUID empresaId, UUID clienteId, UUID varianteId, Integer quantidade)`, `CarrinhoResumo ver(UUID clienteId)`, `void reiniciarDemo(UUID empresaId)`; `CarrinhoResumo(List<ItemCarrinhoResumo> itens, BigDecimal total)`; `ItemCarrinhoResumo(UUID varianteId, String nome, BigDecimal precoUnitario, int quantidade)` com `subtotal()`.
- Produces: `ItemEstoqueView(UUID varianteId, String nome, BigDecimal preco, int quantidade)`, com `nome = nomeCompleto()`. O `index.html` não muda (lê `nome`, `preco`, `quantidade`).

- [ ] **Step 1: ITs que falham** (`@DataJpaTest`, seed da Loja Demo; uma segunda empresa criada no teste quando preciso):
  - T03 `VarianteRepositoryIT.deveBaixarSoComSaldo_quandoQuantidadeNoLimite`: estoque ajustado para 2; baixar 2 → 1 e saldo 0; baixar 3 com saldo 2 → 0 e saldo 2.
  - T04 `deveVenderUltimaUnidadeUmaVez_quandoDuasTransacoesConcorrem`: classe ou método com `@Transactional(propagation = NOT_SUPPORTED)`, estoque 1, duas threads com `TransactionTemplate` e um `CountDownLatch` → soma das linhas afetadas = 1, saldo 0. Restaurar o estoque no fim.
  - T09 `deveNaoAcharVariante_quandoEmpresaDiferente`: `daEmpresa(variante da Loja Demo, outraEmpresa)` → vazio.
  - T10 `ProdutoRepositoryIT.deveIgnorarInativos_quandoCarregaCatalogo`: produto marcado inativo fica fora de `catalogoAtivo`; a variante inativa continua na coleção (filtrada no service) e `daEmpresa` dela → vazio.
  - Review Focus `deveCarregarCatalogoSemNMais1_quandoPercorreColecoes`: com `hibernate.generate_statistics=true`, `catalogoAtivo` + percorrer variantes, valores e opções → `getPrepareStatementCount() <= 4`.
- [ ] **Step 2: Unitários que falham** (`ProdutoServiceTest`, repositórios mockados, entidades montadas em memória):
  - T11 "camisa preta": Camisa Básica (nome 2 + valor "Preta" 1 = 3) antes da Camisa Polo (2).
  - T12 termo `null`, `"de"`, `"a"` → lista vazia, total 0, mensagem `"Nenhum produto encontrado para '<termo>'."`.
  - T13 mesma pontuação: o produto com alguma variante em estoque vem primeiro; os dois com estoque → ordem alfabética do nome.
  - T14 5 produtos que pontuam → 5 e mensagem nula; 6 → 5 e a mensagem atual de "mais de 5".
  - T15 produto com todas as variantes inativas → fora do resultado e fora do total.
  - T16 Preta/M → `opcoes = "Cor: Preta, Tamanho: M"`, `nome = "Camisa Básica (Preta / M)"`; Boné → `opcoes = null`, `nome = "Boné Ajustável"`.
  - T20 `consultarDisponibilidade` com `daEmpresa` vazio → `VarianteNaoEncontradaException("Variante <id> não encontrada.")`.
- [ ] **Step 3: Caracterização das tools (T25, CA10, Review Focus).** Atualizar os JSONs de schema em `src/test/resources/caracterizacao-tools/` (parâmetro `varianteId` nas três tools de ação, descrições novas) e o `CaracterizacaoToolsTest`, que passa a usar `ProdutoService` e `CarrinhoService` com repositórios mockados: `consultar_produto("camisa preta")` devolve `produtoId`, `nome`, `descricao` e `variantes[]` com `varianteId`, `opcoes`, `preco`, `emEstoque`, `quantidadeDisponivel`; `consultar_estoque("P001")` e `adicionar_ao_carrinho("", 1)` → `ToolExecutionException` com `"Variante P001 não encontrada."` / `"Variante  não encontrada."`.
- [ ] **Step 4: `./mvnw test` e `./mvnw verify`** → falham.
- [ ] **Step 5: Implementar.** Busca (RN09) movida do `ProdutoRepositoryEmMemoria` para o `ProdutoService` (mesma normalização, stopwords e pesos), contando os valores de opção das variantes ativas com peso 1. Prompt: trocar "ids de produto" por "ids de variante" e orientar a oferecer outra variante do mesmo produto (tamanho/cor) quando a pedida estiver esgotada. `SimuladorController.estado` usa `listarVariantes(empresaId)`; `/reset` chama `agente.reiniciarConversas()` e `carrinho.reiniciarDemo(empresaId)`.
- [ ] **Step 6: `./mvnw test` e `./mvnw verify`** → passam.
- [ ] **Step 7: Commit** `feat: catálogo com variantes e estoque no banco; tools usam varianteId`.

---

### Task 5: Carrinho no banco e reset

| Campo | Valor |
|---|---|
| ID | T5 (Fase 5) |
| CA/RN | RN02, RN03, RN04, RN05, RN08, RN14, CA05, CA06, CA08, CA09, CA13, CA16, CA17 |
| Arquivos permitidos | `carrinho/**`, `simulador/**`, `CLAUDE.md` e os testes listados em Files |
| Arquivos proibidos | `pom.xml`, migrations, `produto/**` (só consumir), `ai/**` |
| Dependências | T4 |
| Tipo | SEQUENCIAL |
| Tamanho | GRANDE |

Atende: RN02, RN03, RN04, RN05, RN08, RN14, CA05, CA06, CA08, CA09, CA13, CA16, CA17. Matriz: T05, T06, T07, T17, T18, T19, T21, T27.

**Files:**
- Create: `carrinho/Carrinho.java`, `carrinho/ItemCarrinho.java` (entidade), `carrinho/StatusCarrinho.java`, `carrinho/CarrinhoRepository.java`
- Modify: `carrinho/CarrinhoService.java` (implementação no banco, mesma assinatura da Task 4)
- Modify: `CLAUDE.md` (estrutura por domínio com `empresa/` e `cliente/`; banco ligado; `./mvnw test` sem banco e `./mvnw verify` com Testcontainers e Docker; seed em `db/demo`; tirar as menções a `CatalogoMock`, `EstoqueService` e carrinho em memória)
- Test: `carrinho/CarrinhoRepositoryIT.java`, `carrinho/CarrinhoServiceIT.java`, `carrinho/CarrinhoServiceTest.java` (reescrito), `simulador/SimuladorControllerTest.java`

**Interfaces:**
- Consumes: `VarianteRepository.daEmpresa/baixarEstoque/devolverEstoque`, `Variante.nomeCompleto()`, exceções e records da Task 4.
- Produces: `enum StatusCarrinho { ABERTO, CONVERTIDO, ABANDONADO }` (`@Enumerated(STRING)`); `@Entity Carrinho` (`id`, `empresaId`, `clienteId`, `status`, `criadoEm`, `atualizadoEm`, `@OneToMany(mappedBy = "carrinho") @OrderBy("adicionadoEm") List<ItemCarrinho> itens`); `@Entity ItemCarrinho` (`id`, `carrinho`, `@ManyToOne variante`, `quantidade`, `adicionadoEm`).
- Produces: `CarrinhoRepository extends JpaRepository<Carrinho, UUID>` com `@Modifying @Query(nativeQuery = true) int criarAbertoSeNaoExiste(UUID empresaId, UUID clienteId)` (`INSERT ... 'ABERTO' ON CONFLICT (cliente_id) WHERE status = 'ABERTO' DO NOTHING`), `Optional<Carrinho> findByClienteIdAndStatus(UUID clienteId, StatusCarrinho status)`, `@Modifying(clearAutomatically = true, flushAutomatically = true) @Query(nativeQuery = true) int somarItem(UUID carrinhoId, UUID varianteId, int quantidade)` (`INSERT INTO item_carrinho ... ON CONFLICT (carrinho_id, variante_id) DO UPDATE SET quantidade = item_carrinho.quantidade + EXCLUDED.quantidade`) e `List<Carrinho> findByEmpresaIdAndStatus(UUID empresaId, StatusCarrinho status)`.
- `CarrinhoService` (`@Transactional` em `adicionar`, `remover`, `reiniciarDemo`; `readOnly` em `ver`). Ordem em `adicionar`: validar quantidade → `daEmpresa` → `baixarEstoque` (0 linhas → `EstoqueInsuficienteException` com o saldo lido) → `criarAbertoSeNaoExiste` + `findByClienteIdAndStatus` → `somarItem` → resumo com preço atual da variante (RN08).

- [ ] **Step 1: ITs que falham** (`@DataJpaTest` + `@Import(CarrinhoService.class)`, cliente criado no teste):
  - T05 `CarrinhoRepositoryIT.deveCriarUmCarrinhoAberto_quandoChamadoDuasVezes`: `criarAbertoSeNaoExiste` 2 vezes → 1 e 0; uma linha `ABERTO` para o cliente.
  - T06 `deveSomarQuantidade_quandoMesmaVarianteDeNovo`: `somarItem(c, v, 2)` e `somarItem(c, v, 1)` → um item com 3.
  - T07 `CarrinhoServiceIT.deveNaoCriarCarrinhoNemMexerEstoque_quandoEstoqueInsuficiente`: Preta/G (0), adicionar 1 → `EstoqueInsuficienteException`; nenhum carrinho do cliente; estoque continua 0.
  - CA05 `deveBaixarEstoqueEGravarItem_quandoAdiciona`: Preta/M 12, adicionar 2 → estoque 10, 1 item quantidade 2, subtotal `99.80`; adicionar 1 → item 3, estoque 9.
  - Review Focus `deveMostrarEstoqueNovo_quandoConsultaNaMesmaTransacaoDepoisDeAdicionar`: num `TransactionTemplate`, `adicionar(2)` e depois `consultarDisponibilidade` da Preta/M → 10.
  - T21 `deveDevolverEstoqueEApagarCarrinhos_quandoReiniciaDemo`: dois clientes com itens → `reiniciarDemo(lojaDemo)` → estoques iguais aos do seed e 0 carrinhos `ABERTO`; sem carrinhos → nada muda.
- [ ] **Step 2: Unitários que falham** (`CarrinhoServiceTest`, repositórios mockados):
  - T17 item com 3: remover 1 → item 2 e `devolverEstoque(v, 1)`; remover 3 → item sai e `devolverEstoque(v, 3)`; remover `null` → item sai e devolve 3.
  - T18 `adicionar` e `remover` com 0 → `IllegalArgumentException("Quantidade deve ser maior que zero.")` sem tocar repositórios; `adicionar` com 1 → aceito.
  - T19 remover variante fora do carrinho → `ItemNaoEstaNoCarrinhoException("Variante <id> não está no carrinho.")`; nenhum estoque devolvido.
  - Review Focus: cliente sem carrinho aberto → `ver` devolve `itens` vazio e `total` `0.00`; `remover` → `ItemNaoEstaNoCarrinhoException`.
  - T20 (carrinho) `adicionar` com `daEmpresa` vazio → `VarianteNaoEncontradaException` sem baixar estoque.
- [ ] **Step 3: T27 `SimuladorControllerTest`:** `GET /estado?telefone=5511999990000` → JSON com `carrinho.itens[].nome`, `precoUnitario`, `quantidade`, `subtotal`, `total` e `estoque[]` com `varianteId`, `nome`, `preco`, `quantidade`; `POST /reset` → 204, `agente.reiniciarConversas()` e `carrinho.reiniciarDemo(empresaId)` chamados.
- [ ] **Step 4: `./mvnw test` e `./mvnw verify`** → falham.
- [ ] **Step 5: Implementar `CarrinhoService` no banco e as entidades.** Remover o `Map` e os `synchronized`.
- [ ] **Step 6: Atualizar `CLAUDE.md`.**
- [ ] **Step 7: `./mvnw test` (sem Docker) e `./mvnw verify`** → passam. Subir `.\mvnw.cmd spring-boot:run` com o `.env` e conferir no simulador: busca "camisa preta", adicionar 2 Preta/M, painel mostra estoque 10, "Reiniciar demo" volta a 12.
- [ ] **Step 8: Commit** `feat: carrinho persistido no banco e reset que devolve o estoque`.

---

## Fases, arquivos compartilhados e rotas

| Fase | Tarefas | Rota | Motivo |
|---|---|---|---|
| 1 | T1 | 3A (superpowers) | uma tarefa sequencial, fundação de banco e testes |
| 2 | T2 | 3A | uma tarefa pequena, depende do schema |
| 3 | T3 | 3A | uma tarefa, muda a interface pública do agente usada por T4 e T5 |
| 4 | T4 | 3A | uma tarefa grande, mas única e sequencial (o record `Produto` vira entidade e obriga a trocar catálogo, tools e simulador juntos) |
| 5 | T5 | 3A | uma tarefa, integração final e remoção do carrinho em memória |

Nenhuma fase tem 3+ tarefas paralelas, então nenhuma vai para o Orca.

**Arquivos tocados por mais de uma tarefa:** `carrinho/CarrinhoTool.java` (T3, T4), `carrinho/CarrinhoService.java` e `CarrinhoServiceTest.java` (T4, T5), `simulador/SimuladorController.java` e `SimuladorControllerTest.java` (T3, T4, T5), `ai/CaracterizacaoToolsTest.java` (T3, T4).

**Ordem de merge:** T1 → T2 → T3 → T4 → T5, todas commitadas direto na `feat/banco-catalogo-carrinho`, com `./mvnw verify` passando depois de cada uma.
