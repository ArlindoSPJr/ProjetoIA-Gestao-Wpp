# Tools por domínio sobre o Spring AI: plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Trocar a `FerramentasAgente` única por `ProdutoTool` e `CarrinhoTool` (marcadas com `AgentTool`) reunidas por um `ToolRegistry`, mover o agente para `ai/` e passar os retornos ao LLM para `*Response` em `ai/dto/`, sem mudar nada do que o LLM vê.

**Architecture:** O Spring AI continua gerando schemas e despachando pelo nome. `ToolRegistry` recebe `List<AgentTool>` (ordem do `@Order`) e entrega `ToolCallback[]` ao `AgenteSpringAi`. Um teste de caracterização congela nome, descrição, schema e saída de cada tool antes de qualquer mudança e é reapontado para o registry quando as classes novas existem.

**Tech Stack:** Java 21, Spring Boot 4.2.0, Spring AI 2.1 (`@Tool`, `ToolCallbacks`, `ToolCallback`, `ToolContext`), Jackson 3 (`tools.jackson`; anotações em `com.fasterxml.jackson.annotation`), JUnit 5 + AssertJ.

**Spec:** `docs/superpowers/specs/2026-11-23-tools-strategy-spring-ai-design.md`

## Global Constraints

- Pacote base `app.gestaoIAWpp`. Vocabulário de domínio em português.
- Nomes, descrições, `@ToolParam` (texto e `required`) das 5 tools: mover **literalmente** de `FerramentasAgente`; nenhuma string reescrita.
- O LLM vê exatamente o mesmo: schema de entrada e JSON de saída de cada tool idênticos aos da caracterização (T1).
- Regra de negócio fica nos services (`ProdutoService`, `CarrinhoService`); as `*Tool` só delegam e mapeiam.
- O telefone vem do `ToolContext`, nunca do modelo.
- Não mudar loop, memória, advisors, `RegistroTurno` além de pacote/nomes de tipo.
- Branch `feat/tools-strategy-spring-ai`. **Nunca** `git add -A`/`git add .`.
- Testes: `./mvnw test` (Bash, raiz do repositório). Cada tarefa termina com a suíte inteira verde. Testes existentes mantêm as asserções.
- Commits terminam com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. Ordem das tools: `ProdutoTool` antes de `CarrinhoTool` no contexto Spring (`@Order`); teste de contexto na T5.
2. `ToolCallbacks.from` não garante a ordem dos métodos dentro de uma classe: a caracterização compara **por nome**, nunca por posição.
3. Telefone ausente no `ToolContext` continua falhando com `"Telefone ausente no contexto da ferramenta."` antes de qualquer baixa; teste existente migrado na T5.
4. `mensagem` some do JSON quando nula em `ConsultarProdutoResponse`; coberto pelo caso `camisa preta` da T1.
5. Erros de domínio continuam virando `ToolExecutionException` com a mesma mensagem (`P999`, `P002`); casos da T1.

---

## Fase 1: guarda e pacote (SEQUENCIAL)

### Task 1: Caracterização das tools

| ID | Critério de aceite | Permitidos | Proibidos | Deps | Tipo | Tamanho |
|---|---|---|---|---|---|---|
| T1 | `CaracterizacaoToolsTest` verde contra o código atual | o teste novo e `src/test/resources/caracterizacao-tools/*.json` | `src/main` | — | SEQUENCIAL | PEQUENA |

**Files:**
- Test: `src/test/java/app/gestaoIAWpp/agente/CaracterizacaoToolsTest.java`
- Test resources: `src/test/resources/caracterizacao-tools/<nome_da_tool>.json` (5 arquivos, schema de entrada)

**Interfaces:**
- Produces: dois helpers privados, `ToolCallback[] callbacks()` (hoje `ToolCallbacks.from(ferramentas)`) e `ToolCallback callback(String nome)` (busca por nome). Nas T4 e T5 só o `setUp` e `callbacks()` mudam.

- [ ] **Step 1: Escrever o teste.** `setUp` igual ao de `FerramentasAgenteToolsTest` (repositório, estoque, carrinho, `new FerramentasAgente(new ProdutoService(produtos, estoque), carrinho)`, `ToolContext` com `FerramentasAgente.CHAVE_TELEFONE` → `"5511999990000"`). Testes:
  - `nomesDasTools`: nomes de `callbacks()` `containsExactlyInAnyOrder("consultar_produto", "consultar_estoque", "adicionar_ao_carrinho", "remover_do_carrinho", "ver_carrinho")`.
  - `descricoes`: `getToolDefinition().description()` de cada tool igual (`isEqualTo`) ao texto da constante correspondente em `FerramentasAgente` **copiado como literal no teste** (não referenciar a constante: ela será movida). `ver_carrinho`: `"Mostra os itens do carrinho do cliente e o total."`.
  - `schemas`: para cada tool, `JsonMapper.readTree(inputSchema())` igual a `readTree` do arquivo de recurso. Conteúdo dos arquivos (minificado; a igualdade é de árvore JSON):
    - `consultar_produto.json`: `{"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object","properties":{"termo":{"type":"string","description":"Palavras-chave do produto extraídas da mensagem do cliente. Ex.: 'camisa preta', 'tênis branco'."}},"required":["termo"],"additionalProperties":false}`
    - `consultar_estoque.json`: `{"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object","properties":{"produtoId":{"type":"string","description":"Id do produto retornado por consultar_produto."}},"required":["produtoId"],"additionalProperties":false}`
    - `adicionar_ao_carrinho.json`: `{"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object","properties":{"produtoId":{"type":"string","description":"Id do produto retornado por consultar_produto."},"quantidade":{"type":"integer","description":"Quantidade a adicionar, no mínimo 1."}},"required":["produtoId","quantidade"],"additionalProperties":false}`
    - `remover_do_carrinho.json`: `{"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object","properties":{"produtoId":{"type":"string","description":"Id do produto retornado por consultar_produto."},"quantidade":{"type":"integer","description":"Quantidade a remover. Omita para remover tudo."}},"required":["produtoId"],"additionalProperties":false}`
    - `ver_carrinho.json`: `{"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object","properties":{},"required":[],"additionalProperties":false}`
  - Saídas, `callback(nome).call(entrada, ctx)` com `isEqualTo` (string exata):

| Teste | Chamadas | Saída esperada |
|---|---|---|
| `consultarProdutoCamisaPreta` | `consultar_produto` `{"termo":"camisa preta"}` | `{"produtos":[{"id":"P001","nome":"Camisa Básica Preta M","descricao":"Camisa de algodão, gola redonda","preco":49.90,"emEstoque":true,"quantidadeDisponivel":12},{"id":"P002","nome":"Camisa Básica Preta G","descricao":"Camisa de algodão, gola redonda","preco":49.90,"emEstoque":false,"quantidadeDisponivel":0},{"id":"P003","nome":"Camisa Básica Branca M","descricao":"Camisa de algodão, gola redonda","preco":49.90,"emEstoque":true,"quantidadeDisponivel":8},{"id":"P004","nome":"Camisa Polo Azul M","descricao":"Camisa polo de piquet","preco":79.90,"emEstoque":true,"quantidadeDisponivel":5}],"totalEncontrado":4}` |
| `consultarProdutoSemResultado` | `consultar_produto` `{"termo":"geladeira"}` | `{"produtos":[],"totalEncontrado":0,"mensagem":"Nenhum produto encontrado para 'geladeira'."}` |
| `consultarEstoque` | `consultar_estoque` `{"produtoId":"P001"}` | `{"produtoId":"P001","nome":"Camisa Básica Preta M","emEstoque":true,"quantidadeDisponivel":12}` |
| `adicionarAoCarrinho` | `adicionar_ao_carrinho` `{"produtoId":"P001","quantidade":2}` | `{"itens":[{"produtoId":"P001","nome":"Camisa Básica Preta M","precoUnitario":49.90,"quantidade":2,"subtotal":99.80}],"total":99.80}` |
| `removerDoCarrinho` | adicionar P001 ×2, depois `remover_do_carrinho` `{"produtoId":"P001","quantidade":1}` | `{"itens":[{"produtoId":"P001","nome":"Camisa Básica Preta M","precoUnitario":49.90,"quantidade":1,"subtotal":49.90}],"total":49.90}` |
| `verCarrinho` | adicionar P001 ×2, depois `ver_carrinho` `{}` | igual ao de `adicionarAoCarrinho` |
| `produtoInexistente` | `consultar_estoque` `{"produtoId":"P999"}` | lança `ToolExecutionException` com mensagem `"Produto P999 não encontrado."` |
| `removerItemForaDoCarrinho` | `remover_do_carrinho` `{"produtoId":"P002"}` | lança `ToolExecutionException` com mensagem `"Produto P002 não está no carrinho."` |

- [ ] **Step 2:** `./mvnw test -Dtest=CaracterizacaoToolsTest` → PASS. É caracterização: tem que passar já contra o código atual; se falhar, o esperado foi copiado errado (arquivos em UTF-8).
- [ ] **Step 3:** Commit: `test: caracteriza schemas e saídas das tools do Spring AI`.

### Task 2: Mover `agente/` para `ai/` e renomear os DTOs do agente

| ID | Critério de aceite | Permitidos | Proibidos | Deps | Tipo | Tamanho |
|---|---|---|---|---|---|---|
| T2 | Pacote `agente/` vazio (main e test); suíte verde; nenhuma mudança de lógica | `agente/**`, `ai/**`, `atendimento/AtendimentoService.java`, `simulador/SimuladorController.java`, testes que importam essas classes | `produto/**`, `carrinho/**`, `index.html`, prompt, `application.yaml` | T1 | SEQUENCIAL | PEQUENA |

**Files:**
- `git mv` de `src/main/java/app/gestaoIAWpp/agente/` para `.../ai/`: `AgenteAtendimento`, `AgenteSpringAi`, `AgenteProperties`, `LimiteIteracoesAdvisor`, `RegistroTurno`, `FerramentasAgente` (temporário, sai na T5).
- `git mv` e renome para `.../ai/dto/`: `RespostaAgente` → `AgenteResponse`, `IteracaoTurno` → `IteracaoTurnoResponse`, `ToolPedida` → `ToolPedidaResponse` (mesmos componentes).
- `git mv` dos testes de `src/test/.../agente/` para `.../ai/`: `AgenteSpringAiTest`, `FerramentasAgenteToolsTest`, `LimiteIteracoesAdvisorTest`, `RegistroTurnoTest`, `CaracterizacaoToolsTest`.
- Modify: imports em `AtendimentoService`, `SimuladorController`, `AtendimentoServiceTest`, `SimuladorControllerTest`.

**Interfaces:**
- Produces: `app.gestaoIAWpp.ai.dto.AgenteResponse(String texto, List<IteracaoTurnoResponse> iteracoes)`, `IteracaoTurnoResponse(int numero, String finishReason, long tokensEntrada, long tokensSaida, List<ToolPedidaResponse> toolsPedidas, String observacao)`, `ToolPedidaResponse(String nome, String entradaJson)`. O JSON do simulador não muda.

- [ ] **Step 1:** `git mv`, renomes, `package` e imports; atualizar Javadoc que cite os nomes antigos.
- [ ] **Step 2:** `./mvnw test` → mesma contagem de testes de antes da T2, tudo PASS.
- [ ] **Step 3:** Commit: `refactor: move o agente para o pacote ai e renomeia DTOs para *Response`.

## Fase 2: fundação (SEQUENCIAL)

### Task 3: `AgentTool`, `ContextoTool` e `ToolRegistry`

| ID | Critério de aceite | Permitidos | Proibidos | Deps | Tipo | Tamanho |
|---|---|---|---|---|---|---|
| T3 | Testes da T3 verdes com tools falsas; nenhuma classe existente alterada | os arquivos novos abaixo | `AgenteSpringAi`, `FerramentasAgente`, domínios | T2 | SEQUENCIAL | PEQUENA |

**Files:**
- Create em `src/main/java/app/gestaoIAWpp/ai/`: `AgentTool.java`, `ContextoTool.java`, `ToolRegistry.java`
- Test: `src/test/java/app/gestaoIAWpp/ai/ToolRegistryTest.java`, `ContextoToolTest.java`

**Interfaces:**
- Produces:
  - `public interface AgentTool {}`: marcadora; Javadoc diz que a implementação é um `@Component` com métodos `@Tool` e `@Order` para a ordem.
  - `public final class ContextoTool`: `public static final String CHAVE_TELEFONE = "telefone"` e `public static String telefone(ToolContext ctx)`, com corpo e mensagem copiados de `FerramentasAgente.telefone`.
  - `public class ToolRegistry` (**sem** `@Component` nesta tarefa; entra na T4): `ToolRegistry(List<AgentTool> tools)`, `ToolCallback[] callbacks()` (cópia defensiva). O construtor converte cada `AgentTool` com `ToolCallbacks.from(tool)`, junta na ordem da lista e lança `IllegalStateException("Tool duplicada: <nome>")` se um nome se repetir entre classes.

- [ ] **Step 1: Testes que falham.**
  - `ToolRegistryTest`, com duas classes internas que implementam `AgentTool` e têm métodos `@Tool(name = ...)` triviais:
    - `juntaAsToolsDeTodasAsClasses`: classes com `a` e `b`,`c` → nomes `containsExactlyInAnyOrder("a", "b", "c")`.
    - `respeitaAOrdemDaLista`: a tool da primeira classe vem antes das da segunda no array.
    - `nomeDuplicadoEntreClassesFalha`: duas classes com tool `a` → `IllegalStateException` com mensagem contendo `"Tool duplicada: a"`.
    - `callbacksDevolveCopia`: alterar o array devolvido não altera o próximo `callbacks()`.
  - `ContextoToolTest`: contexto com `CHAVE_TELEFONE` → devolve o telefone; contexto sem a chave e `null` → `IllegalStateException` `"Telefone ausente no contexto da ferramenta."`.
- [ ] **Step 2:** `./mvnw test -Dtest='ToolRegistryTest,ContextoToolTest'` → FAIL (não compila).
- [ ] **Step 3:** Implementar as três classes.
- [ ] **Step 4:** `./mvnw test` → tudo PASS.
- [ ] **Step 5:** Commit: `feat: AgentTool, ContextoTool e ToolRegistry`.

## Fase 3: tools por domínio (SEQUENCIAL)

### Task 4: `ProdutoTool`, `CarrinhoTool` e DTOs de resposta

| ID | Critério de aceite | Permitidos | Proibidos | Deps | Tipo | Tamanho |
|---|---|---|---|---|---|---|
| T4 | `CaracterizacaoToolsTest` verde **contra o `ToolRegistry`** com as classes novas; `ToolRegistry` vira bean | `produto/ProdutoTool.java`, `carrinho/CarrinhoTool.java`, `ai/dto/` (5 records novos), `ai/ToolRegistry.java` (só `@Component`), `CaracterizacaoToolsTest` (setUp e `callbacks()`) | `FerramentasAgente`, `AgenteSpringAi`, services, records de domínio | T3 | SEQUENCIAL | PEQUENA |

**Files:**
- Create: `src/main/java/app/gestaoIAWpp/produto/ProdutoTool.java`, `src/main/java/app/gestaoIAWpp/carrinho/CarrinhoTool.java`
- Create em `src/main/java/app/gestaoIAWpp/ai/dto/`: `ConsultarProdutoResponse`, `ProdutoResponse`, `ConsultarEstoqueResponse`, `CarrinhoResponse`, `ItemCarrinhoResponse`
- Modify: `ai/ToolRegistry.java` (`@Component`), `src/test/.../ai/CaracterizacaoToolsTest.java`

**Interfaces:**
- Consumes: `AgentTool`, `ContextoTool.telefone`, `ToolRegistry` (T3); `ProdutoService.buscar/consultarDisponibilidade`; `CarrinhoService.adicionar/remover/ver`.
- Produces:
  - `@Component @Order(1) public class ProdutoTool implements AgentTool`, construtor `(ProdutoService produtoService)`. Métodos `@Tool` `ConsultarProdutoResponse consultarProduto(String termo)` e `ConsultarEstoqueResponse consultarEstoque(String produtoId)`, com anotações e textos movidos de `FerramentasAgente`. `public static final String DESCRICAO_PRODUTO_ID` (texto atual).
  - `@Component @Order(2) public class CarrinhoTool implements AgentTool`, construtor `(CarrinhoService carrinho)`. Métodos `@Tool` `CarrinhoResponse adicionarAoCarrinho(String produtoId, Integer quantidade, ToolContext ctx)` (mantém o `if (quantidade == null)` com a mesma mensagem), `CarrinhoResponse removerDoCarrinho(String produtoId, Integer quantidade, ToolContext ctx)` (`required = false` na quantidade), `CarrinhoResponse verCarrinho(ToolContext ctx)`. Telefone por `ContextoTool.telefone(ctx)`. Usa `ProdutoTool.DESCRICAO_PRODUTO_ID`.
  - Records (ordem dos componentes = ordem das chaves no JSON; não reordenar):
    - `ProdutoResponse(String id, String nome, String descricao, BigDecimal preco, boolean emEstoque, int quantidadeDisponivel)`
    - `ConsultarProdutoResponse(List<ProdutoResponse> produtos, int totalEncontrado, @JsonInclude(JsonInclude.Include.NON_NULL) String mensagem)`
    - `ConsultarEstoqueResponse(String produtoId, String nome, boolean emEstoque, int quantidadeDisponivel)`
    - `ItemCarrinhoResponse(String produtoId, String nome, BigDecimal precoUnitario, int quantidade, BigDecimal subtotal)` (subtotal de `ItemCarrinho.subtotal()`)
    - `CarrinhoResponse(List<ItemCarrinhoResponse> itens, BigDecimal total)`
  - O mapeamento domínio → `*Response` é privado em cada `*Tool`; `ai/dto` não importa domínio.

- [ ] **Step 1: Reapontar a caracterização (vermelho).** Em `CaracterizacaoToolsTest`, `setUp` passa a montar `registry = new ToolRegistry(List.of(new ProdutoTool(new ProdutoService(produtos, estoque)), new CarrinhoTool(carrinho)))`; `callbacks()` devolve `registry.callbacks()`; o contexto usa `ContextoTool.CHAVE_TELEFONE`. Nenhum valor esperado muda.
- [ ] **Step 2:** `./mvnw test -Dtest=CaracterizacaoToolsTest` → FAIL (classes não existem).
- [ ] **Step 3:** Implementar os DTOs, `ProdutoTool`, `CarrinhoTool` e anotar `ToolRegistry` com `@Component`.
- [ ] **Step 4:** `./mvnw test` → tudo PASS. A `FerramentasAgente` ainda existe e continua sendo a usada pelo `AgenteSpringAi`; as duas convivem só até a T5.
- [ ] **Step 5:** Commit: `feat: ProdutoTool e CarrinhoTool com DTOs de resposta`.

## Fase 4: integração e remoção (SEQUENCIAL)

### Task 5: `AgenteSpringAi` usa o registry; remover `FerramentasAgente`

| ID | Critério de aceite | Permitidos | Proibidos | Deps | Tipo | Tamanho |
|---|---|---|---|---|---|---|
| T5 | `FerramentasAgente` removida; testes migrados com as mesmas asserções; ordem das tools no contexto garantida; `CLAUDE.md` atualizado | `ai/AgenteSpringAi.java`, `ai/FerramentasAgente.java` (remover), testes em `src/test/.../ai/`, `GestaoIaWppApplicationTests`, seções "Estrutura da demo" e "Regra das camadas" do `CLAUDE.md` | domínios, `index.html`, prompt, resto do `CLAUDE.md` | T4 | SEQUENCIAL | PEQUENA |

**Files:**
- Modify: `src/main/java/app/gestaoIAWpp/ai/AgenteSpringAi.java`
- Delete: `src/main/java/app/gestaoIAWpp/ai/FerramentasAgente.java`
- Move/rename: `src/test/.../ai/FerramentasAgenteToolsTest.java` → `ToolsAgenteTest.java`
- Modify: `AgenteSpringAiTest` (setUp), `GestaoIaWppApplicationTests`, `CLAUDE.md`

**Interfaces:**
- Consumes: `ToolRegistry.callbacks()`, `ContextoTool.CHAVE_TELEFONE`, `ProdutoTool`, `CarrinhoTool`.
- Produces: `AgenteSpringAi(ChatClient.Builder builder, ToolCallingManager manager, ToolRegistry tools, AgenteProperties props)`, com `.defaultToolCallbacks(tools.callbacks())` no lugar de `ToolCallbacks.from(ferramentas)`, e `ContextoTool.CHAVE_TELEFONE` no `toolContext`. Nada mais muda na classe.

- [ ] **Step 1: Teste de contexto.** Em `GestaoIaWppApplicationTests`, `registryTemAsCincoToolsComProdutoAntesDeCarrinho`: com o `ToolRegistry` do contexto, os nomes contêm exatamente as 5 tools, e o índice de cada tool de produto (`consultar_produto`, `consultar_estoque`) é menor que o de cada tool de carrinho. Passa já agora; vira guarda da troca.
- [ ] **Step 2:** Trocar o `AgenteSpringAi` para `ToolRegistry`. Setup dos testes:
  - `AgenteSpringAiTest`: `new ToolRegistry(List.of(new ProdutoTool(new ProdutoService(produtos, estoque)), new CarrinhoTool(carrinho)))` no lugar da `FerramentasAgente`; referências a `FerramentasAgente.CHAVE_TELEFONE` viram `ContextoTool.CHAVE_TELEFONE`.
  - `ToolsAgenteTest` (ex-`FerramentasAgenteToolsTest`): `callback(nome)` busca em `registry.callbacks()`; chamadas diretas `ferramentas.adicionarAoCarrinho(...)` viram `carrinhoTool.adicionarAoCarrinho(...)`; `CHAVE_TELEFONE` de `ContextoTool`.
  - **Nenhuma asserção muda** (só setup, imports, nomes de variável e o tipo de retorno onde o teste guarda o retorno).
- [ ] **Step 3:** `git rm` da `FerramentasAgente`; `grep -rn "FerramentasAgente" src` não encontra nada.
- [ ] **Step 4:** `./mvnw test` → tudo PASS, incluindo `CaracterizacaoToolsTest` com os valores da T1.
- [ ] **Step 5:** `CLAUDE.md`: na "Estrutura da demo", `agente/` vira `ai/` (`AgenteAtendimento`, `AgenteSpringAi`, `LimiteIteracoesAdvisor`, `RegistroTurno`, `ToolRegistry`, `AgentTool`, `ContextoTool`, `dto/` com os `*Response`); `produto/` ganha `ProdutoTool` e `carrinho/` ganha `CarrinhoTool`; frase "para criar uma tool: um `@Component` num pacote de domínio que implementa `AgentTool`, com `@Order`". Na "Regra das camadas", `FerramentasAgente` vira `ProdutoTool`/`CarrinhoTool` e o telefone passa a vir do `ToolContext` via `ContextoTool`. Também trocar `RespostaAgente.iteracoes` por `AgenteResponse.iteracoes` e `agente/` por `ai/` nas outras menções do arquivo.
- [ ] **Step 6:** Commit: `refactor: agente usa o ToolRegistry e remove FerramentasAgente`.

---

## Conflitos, ordem de merge e roteamento

- **Arquivos tocados por mais de uma tarefa:** `CaracterizacaoToolsTest` (T1, T2, T4), `ToolRegistry` (T3, T4), `AgenteSpringAi` e `AgenteSpringAiTest` (T2, T5), `FerramentasAgenteToolsTest`/`ToolsAgenteTest` (T2, T5), `ai/dto/` (T2, T4; arquivos distintos), `CLAUDE.md` (T5).
- **Ordem de merge:** linear na branch `feat/tools-strategy-spring-ai`: T1 → T2 → T3 → T4 → T5.
- **Roteamento:** todas as fases em **4A (subagent-driven-development)**: tarefas pequenas, encadeadas e com arquivos de teste compartilhados; nenhuma fase tem 3 ou mais tarefas paralelas grandes.
- **Integração (depois da revisão final):** push da branch e PR para `origin/main`, que leva junto os 22 commits locais do Spring AI; merge do PR, conforme você pediu.
