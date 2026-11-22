# Migração do agente para Spring AI 2.1 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Trocar o loop manual de function calling (`AgenteClaude`, SDK `anthropic-java`) pelo Spring AI 2.1, com tools `@Tool`, loop do `ToolCallingAdvisor` e memória do framework. A regra de negócio das tools sai do adaptador e vai para os services de domínio.

**Architecture:**
- **Domínio:**
  - `produto/ProdutoService`: busca com ranking, corte em 5 e disponibilidade.
  - `carrinho/CarrinhoService`: o carrinho, que já existe.
- **Adaptador:** `agente/FerramentasAgente`, com os métodos `@Tool`. Ele só traduz a entrada do modelo e o `ToolContext` para chamadas aos services.
- **Agente:** `AgenteSpringAi` usa um `ChatClient` com a cadeia de advisors abaixo:
  - `LimiteIteracoesAdvisor` (estende o `ToolCallingAdvisor`): faz o loop e corta em 5 chamadas à API;
  - `MessageChatMemoryAdvisor` dentro do loop;
  - `SimpleLoggerAdvisor`.
- **Registro:** o `RegistroTurno` guarda o resumo de cada iteração para a página.

**Tech Stack:** Java 21, Spring Boot 4.2.0, Spring AI 2.1.0 (`spring-ai-starter-model-anthropic`), JUnit 5 + Mockito + AssertJ.

**Spec:** `docs/superpowers/specs/2026-11-21-migracao-spring-ai-design.md`. As decisões e os fatos do framework, verificados no código-fonte, estão lá. Leia antes de começar.

## Global Constraints

- Spring AI **2.1.0** via `spring-ai-bom`. Nenhum outro framework de IA (LangChain4j, LangGraph4j).
- Modelo `claude-haiku-5-5`, effort `low`, `maxTokens` 16000, tirados de `AgenteProperties` (bloco `agente:`).
- `agente.max-iteracoes: 5` conta **chamadas à API por turno**. `spring.ai.tools.limits.max-total-tool-calls: 10` conta tools por turno (`THROW`).
- Nomes das tools, exatamente: `consultar_produto`, `consultar_estoque`, `adicionar_ao_carrinho`, `remover_do_carrinho`, `ver_carrinho`. Parâmetros camelCase: `termo`, `produtoId`, `quantidade`. Sem `strict`.
- **Camadas:** os métodos `@Tool` não acessam `ProdutoRepository` nem `EstoqueService` direto e não contêm regra de negócio. Eles delegam a `ProdutoService` / `CarrinhoService`. O adaptador só valida a presença de parâmetros e lê o telefone do `ToolContext`.
- Mensagem de fallback, exatamente: `Desculpe, não consegui concluir agora. Vou chamar um atendente para te ajudar.`
- Logs: só os do framework (`SimpleLoggerAdvisor`), mais o `log.warn` de falha do turno.
- Memória: `MessageWindowChatMemory` com a janela padrão (20) + `InMemoryChatMemoryRepository`.
- Um turno que falha restaura a memória do telefone ao estado de antes dele. Um reset durante o turno não ressuscita a conversa. O lock por telefone continua.
- Contrato HTTP final de `POST /api/simulador/mensagens`: `{texto, iteracoes}`.
- Os testes nunca chamam a API real (mock de `ChatModel`). O `contextLoads` sobe sem chave e sem banco.
- Vocabulário de domínio em português, e o estilo atual: records, injeção por construtor, AssertJ.
- O `.env` nunca entra em commit. Cada commit termina com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Uma dúvida que este plano não resolva, ou qualquer mudança de fluxo ou de padrão de código, vai **ao usuário antes de continuar**.

## Review Focus

1. **Exceção numa rodada depois de um `tool_use`.** A memória não pode ficar com um `tool_use` sem `tool_result`, senão o turno seguinte quebra com erro 400 (`falhaDepoisDeToolNaoDeixaToolUseNaMemoria`, T7).
2. **Entrada de tool com formato inválido** (`"quantidade": "dois"`). Vira erro para o modelo, o turno continua e o estoque não muda (`entradaComFormatoInvalidoVoltaAoModeloComoErro`, T7).
3. **Reset durante um turno em andamento.** A resposta vai ao cliente, mas a conversa antiga não volta (`resetDuranteTurnoNaoRessuscitaConversa`, T7).
4. **Telefone ausente no `ToolContext`.** O erro deve ser claro, e nunca o carrinho de outro telefone (`semTelefoneNoContextoFalhaComMensagemClara`, T5).
5. **Memória cheia (janela de 20).** O histórico enviado sempre começa numa mensagem do cliente (`janelaCheiaComecaEmMensagemDoCliente`, T7).

## Dependências e ondas

| Task | Entrega | Arquivos principais | Depende de | Onda |
|---|---|---|---|---|
| T1 | Spring AI no pom e na config | `pom.xml`, `application.yaml` | — | 1 |
| T2 | `ProdutoService` + records do domínio | `produto/*` | T1 | 2 |
| T3 | `RegistroTurno`, `IteracaoTurno`, `ToolPedida` | `agente/` (arquivos novos) | T1 | 2 |
| T4 | Página no formato novo | `static/index.html` | T1 | 2 |
| T5 | Métodos `@Tool` no `FerramentasAgente` | `agente/FerramentasAgente.java` | T2 | 3 |
| T6 | `LimiteIteracoesAdvisor` | `agente/` (arquivo novo) | T3 | 3 |
| T7 | `AgenteSpringAi` + remoção do loop manual | `agente/*`, `simulador` (teste), `pom.xml` | T5, T6 | 4 |
| T8 | Documentação + verificação final | `CLAUDE.md`, `docs/contexto.md` | T7 | 5 |

As tarefas da mesma onda não mexem nos mesmos arquivos. Durante as ondas 2 e 3, o `AgenteClaude` continua sendo o agente ativo. O `FerramentasAgente` mantém o `definicoes()` e o `executar(...)` antigos, delegando para o código novo, até a T7 remover o loop manual. A página (T4) só bate com o backend depois da T7, o que é esperado: os testes não cobrem a página.

---

### Task 1: Spring AI no projeto

**Files:** Modify `pom.xml`, `src/main/resources/application.yaml`, `src/test/java/app/gestaoIAWpp/GestaoIaWppApplicationTests.java`

**Interfaces:**
- Produces: os beans `ChatClient.Builder` (prototype) e `ToolCallingManager`, autoconfigurados.

- [ ] **Step 0: Branches** (o controller faz antes de despachar a onda 1).
  - Commit das mudanças pendentes em `feature/logs-iteracoes`: `feat: painel de iterações e logs do loop manual`.
  - `git switch -c feature/spring-ai`. Essa é a branch de integração.
- [ ] **Step 1: Teste que falha**, em `GestaoIaWppApplicationTests`:

```java
@Autowired ApplicationContext contexto;

@Test
void springAiConfigurado() {
    assertThat(contexto.getBeanProvider(ChatClient.Builder.class).getIfAvailable()).isNotNull();
    assertThat(contexto.getBean(ToolCallingManager.class)).isNotNull();
}
```

- [ ] **Step 2:** `./mvnw test -Dtest=GestaoIaWppApplicationTests`. Esperado: FAIL (compilação).
- [ ] **Step 3: pom.**
  - Adicionar `<spring-ai.version>2.1.0</spring-ai.version>`, o import do `spring-ai-bom` em `dependencyManagement` e o `spring-ai-starter-model-anthropic`.
  - Subir o `anthropic-java` direto de 3.3.0 para **3.8.0**, para não brigar com o `anthropic-java-core` 3.8.0 do Spring AI. Ele sai na T7.
  - Atualizar o comentário sobre o Spring AI: pgvector e advisor de RAG continuam fora.
- [ ] **Step 4: application.yaml.**
  - Adicionar o bloco abaixo e trocar o `logging` existente por ele.
  - O comentário de `agente.max-iteracoes` passa a ser: "chamadas à API por turno".

```yaml
spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY:}
    tools:
      limits:
        # Rede de segurança. O limite principal é agente.max-iteracoes (chamadas à API por turno).
        max-total-tool-calls: 10
logging:
  level:
    # Troque para DEBUG para ver no terminal a requisição e a resposta de cada rodada do loop.
    org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor: INFO
```

- [ ] **Step 5:** `./mvnw test`. Esperado: tudo verde (73 + 1). Se a `api-key` vazia quebrar o `contextLoads`, **pare e pergunte ao usuário**.
- [ ] **Step 6: Commit** `build: adiciona Spring AI 2.1 (starter Anthropic) e limites de tools`.

---

### Task 2: `ProdutoService` (regra de negócio da busca)

**Files:**
- Create: `produto/ProdutoService.java`, `produto/ProdutoResumo.java`, `produto/ResultadoBusca.java`, `produto/DisponibilidadeProduto.java`
- Test: `produto/ProdutoServiceTest.java`
- Modify: `agente/FerramentasAgente.java` (o `executar` antigo passa a delegar a busca e a disponibilidade), `agente/FerramentasAgenteTest.java` e `agente/AgenteClaudeTest.java` (só o `setUp`: novo argumento do construtor)

**Interfaces:**
- Consumes: `ProdutoRepository.buscar(String) -> List<ProdutoEncontrado>`, `buscarPorId(String) -> Optional<Produto>`, `EstoqueService.consultar(String) -> int`.
- Produces:
  - `@Service ProdutoService(ProdutoRepository, EstoqueService)`
  - `static final int LIMITE_RESULTADOS_BUSCA = 5`
  - `ResultadoBusca buscar(String termo)`
  - `DisponibilidadeProduto consultarDisponibilidade(String produtoId)` (lança `ProdutoNaoEncontradoException`)
  - `record ProdutoResumo(String id, String nome, String descricao, BigDecimal preco, boolean emEstoque, int quantidadeDisponivel)`
  - `record ResultadoBusca(List<ProdutoResumo> produtos, int totalEncontrado, @JsonInclude(JsonInclude.Include.NON_NULL) String mensagem)`
  - `record DisponibilidadeProduto(String produtoId, String nome, boolean emEstoque, int quantidadeDisponivel)`

Mover sem mudar o comportamento, a partir do `FerramentasAgente.consultarProduto`, `resumoProduto` e `consultarEstoque`:
- a ordenação: pontuação desc, depois em estoque, depois id;
- o corte em 5;
- os dois textos de `mensagem`, idênticos.

- [ ] **Step 1: Testes que falham** (`ProdutoServiceTest`). Portar do `FerramentasAgenteTest` as asserções de valores, agora sobre os records:
  - `buscarRetornaIdPrecoEEstoque`
  - `buscarComMaisDeCincoLimitaEPedeDetalhes`
  - `empateDePontuacaoPoeComEstoqueAntesDeEsgotado`
  - `buscarComAteCincoNaoTemMensagem` (`mensagem()` é `null`)
  - `buscarSemResultadoTemMensagemENaoLanca`
  - `consultarDisponibilidadeRetornaQuantidade`
  - `consultarDisponibilidadeDeProdutoInexistenteLanca` (`Produto P999 não encontrado.`)
  - `resultadoSemMensagemNaoSerializaOCampo`: `JsonMapper.builder().build().writeValueAsString(...)` não contém `"mensagem"`.
- [ ] **Step 2:** `./mvnw test -Dtest=ProdutoServiceTest`. Esperado: FAIL.
- [ ] **Step 3: Implementar.** Fazer o `FerramentasAgente` receber `ProdutoService` no construtor e trocar os privados `consultarProduto` / `consultarEstoque` por chamadas ao service. O `executar` continua serializando o resultado: os records geram o mesmo JSON do `Map`. Atualizar o `setUp` dos dois testes do agente.
- [ ] **Step 4:** `./mvnw test`. Todos verdes, inclusive o `FerramentasAgenteTest` **sem mudar asserções**.
- [ ] **Step 5: Commit** `refactor: regra de busca e disponibilidade no ProdutoService`.

---

### Task 3: Registro do turno

**Files:**
- Create: `agente/ToolPedida.java`, `agente/IteracaoTurno.java`, `agente/RegistroTurno.java`
- Test: `agente/RegistroTurnoTest.java`

**Interfaces:**
- Produces:
  - `record ToolPedida(String nome, String entradaJson)`
  - `record IteracaoTurno(int numero, String finishReason, long tokensEntrada, long tokensSaida, List<ToolPedida> toolsPedidas, String observacao)`
  - `final class RegistroTurno`, um por turno e sem uso concorrente:
    - `public static final String CHAVE = "registroTurno"`
    - `int iniciarIteracao()` (devolve o número, começando em 1)
    - `void concluirIteracao(String finishReason, long tokensEntrada, long tokensSaida, List<ToolPedida> toolsPedidas)`
    - `void observar(String texto)` (na iteração mais recente)
    - `int iteracoesIniciadas()`
    - `List<IteracaoTurno> iteracoes()` (cópia imutável)
    - `static RegistroTurno de(Map<String, ?> contexto)` (devolve `null` se ausente)

- [ ] **Step 1: Testes que falham:**
  - `iteracoesNaOrdemComDadosDaResposta`: iniciar, concluir `("tool_use", 10, 5, List.of(new ToolPedida("ver_carrinho", "{}")))`, iniciar, concluir `("end_turn", 20, 8, List.of())`. Resultado: 2 iterações, com números 1 e 2 e os campos exatos.
  - `observacaoVaiParaAUltimaIteracao`.
  - `iteracaoIniciadaSemConclusaoApareceComNumero`: iniciar sem concluir e chamar `observar("falha")`. Resultado: 1 iteração, `finishReason` null e observação `falha`.
  - `deDevolveRegistroOuNull`.
- [ ] **Step 2:** rodar. Esperado: FAIL.
- [ ] **Step 3: Implementar.**
- [ ] **Step 4:** `./mvnw test`, em verde.
- [ ] **Step 5: Commit** `feat: registro das iterações do turno`.

---

### Task 4: Página no formato novo

**Files:** Modify `src/main/resources/static/index.html`

**Interfaces:**
- Consumes: o JSON `{texto, iteracoes: [IteracaoTurno]}`, com os campos `numero`, `finishReason`, `tokensEntrada`, `tokensSaida`, `toolsPedidas[{nome, entradaJson}]` e `observacao`.

- [ ] **Step 1:** Reescrever `caixaIteracao`:
  - título `Iteração N`, com as etiquetas `finish: <finishReason>` (as classes CSS `tool_use` e `end_turn` continuam) e `tokens: E entrada / S saída`;
  - bloco "Tools pedidas pelo modelo", com uma linha por tool: o nome em negrito e o `entradaJson` em `<pre>`;
  - observação em vermelho.

  Remover a etapa "Enviado à API", os `<details>` de JSON, `blocosLegiveis`, `caixaChamada` e o CSS que ficar sem uso.

  `mostrarIteracoes`: o resumo (total de iterações e de tokens) ganha a nota `Resultado das tools e JSON de cada rodada: ligue o SimpleLoggerAdvisor em DEBUG no application.yaml.`
- [ ] **Step 2:** Extrair o `<script>` e rodar `node --check`. Esperado: sem erro.
- [ ] **Step 3: Commit** `feat: painel de iterações no formato do Spring AI`.

---

### Task 5: Métodos `@Tool` no `FerramentasAgente`

**Files:**
- Modify: `agente/FerramentasAgente.java`
- Test: `agente/FerramentasAgenteToolsTest.java` (novo; o `FerramentasAgenteTest` antigo continua até a T7)

**Interfaces:**
- Consumes: `ProdutoService` (T2) e `CarrinhoService.adicionar(String, String, int)`, `remover(String, String, Integer)`, `ver(String)`.
- Produces, no `FerramentasAgente`:
  - `public static final String CHAVE_TELEFONE = "telefone"`
  - métodos públicos com `@Tool(name = …, description = …)` e `@ToolParam(description = …)`:
    - `ResultadoBusca consultarProduto(String termo)`
    - `DisponibilidadeProduto consultarEstoque(String produtoId)`
    - `CarrinhoResumo adicionarAoCarrinho(String produtoId, Integer quantidade, ToolContext ctx)`
    - `CarrinhoResumo removerDoCarrinho(String produtoId, @ToolParam(required = false) Integer quantidade, ToolContext ctx)`
    - `CarrinhoResumo verCarrinho(ToolContext ctx)`

Regras do adaptador:
- As descrições vêm do `definicoes()` atual, trocando `produto_id` por `produtoId`.
- Cada método só delega. Sem acesso a repositório ou estoque, e sem regra de negócio.
- Validações do adaptador:
  - quantidade `null` em `adicionarAoCarrinho`: `IllegalArgumentException("Parâmetro obrigatório ausente: quantidade.")`;
  - telefone ausente no contexto: `IllegalStateException("Telefone ausente no contexto da ferramenta.")`.
- A quantidade ≥ 1 já é validada pelo domínio (`Quantidade deve ser maior que zero.`).
- O `executar(...)` antigo passa a chamar esses métodos, montando o `ToolContext` com o telefone. O `definicoes()` fica como está até a T7.

- [ ] **Step 1: Testes que falham** (`FerramentasAgenteToolsTest`, com `ctx = new ToolContext(Map.of(CHAVE_TELEFONE, TEL))`):
  - `callbacksTemAsCincoToolsComOsNomesDaLoja`: os nomes de `ToolCallbacks.from(ferramentas)` são os 5. No schema de `adicionar_ao_carrinho`, `produtoId` e `quantidade` são required. No de `remover_do_carrinho`, só `produtoId`.
  - `adicionarUsaTelefoneDoContextoEBaixaEstoque`: P001, 2 unidades → o carrinho de `TEL` tem 2 e o estoque de P001 é 10.
  - `removerSemQuantidadeRemoveTudo`.
  - `quantidadeZeroLancaSemBaixa`: lança `IllegalArgumentException`, e o estoque de P001 continua 12.
  - `estoqueInsuficienteLanca`: mensagem contém `Estoque insuficiente`.
  - `semTelefoneNoContextoFalhaComMensagemClara`: `IllegalStateException` com `Telefone ausente`, e o estoque continua 12.
  - `callbackConverteEntradaESerializaResultado`: o callback de `consultar_produto` com `call("{\"termo\":\"jaqueta\"}", ctx)` devolve um JSON com `"produtos"` e `"totalEncontrado"`.
  - `callbackComFormatoInvalidoLancaToolExecutionException`: o callback de `adicionar_ao_carrinho` com `{"produtoId":"P001","quantidade":"dois"}` lança `ToolExecutionException`, e o estoque continua 12.
- [ ] **Step 2:** rodar. Esperado: FAIL.
- [ ] **Step 3: Implementar.**
- [ ] **Step 4:** `./mvnw test`, todos verdes. O `FerramentasAgenteTest` continua passando. A única asserção que pode mudar é a do texto de quantidade inválida, que agora vem do domínio: ajuste-a para `Quantidade deve ser maior que zero.`
- [ ] **Step 5: Commit** `feat: tools com @Tool no FerramentasAgente delegando ao domínio`.

---

### Task 6: `LimiteIteracoesAdvisor`

**Files:**
- Create: `agente/LimiteIteracoesAdvisor.java`
- Test: `agente/LimiteIteracoesAdvisorTest.java`

**Interfaces:**
- Consumes: `RegistroTurno`, `ToolPedida` (T3), lidos do contexto da requisição/resposta pela chave `RegistroTurno.CHAVE`.
- Produces:
  - `public class LimiteIteracoesAdvisor extends ToolCallingAdvisor`, com o construtor `LimiteIteracoesAdvisor(ToolCallingManager manager, int maxIteracoes)`, que chama `super(manager, DEFAULT_TOOL_EXECUTION_ELIGIBILITY_CHECKER, ToolCallingAdvisor.DEFAULT_ORDER, false)`. O javadoc diz que a cadeia exige um `MessageChatMemoryAdvisor` downstream (order maior).
  - `public static final String FINISH_LIMITE = "limiteIteracoes"`.
  - `doBeforeCall`: `registro.iniciarIteracao()`. Sem registro no contexto, lança `IllegalStateException`.
  - `doAfterCall`:
    - conclui a iteração com o finish reason da 1ª generation, `usage.getPromptTokens()` / `getCompletionTokens()` (0 se nulo) e uma `ToolPedida(nome, arguments)` para cada `ToolCall` das generations;
    - se `chatResponse.hasToolCalls()` e `registro.iteracoesIniciadas() >= maxIteracoes`, chama `registro.observar("Atingiu o limite de " + max + " chamadas à API; as tools pedidas não foram executadas.")` e devolve a resposta com uma única `Generation(new AssistantMessage(""), ChatGenerationMetadata.builder().finishReason(FINISH_LIMITE).build())`, o que encerra o loop.

- [ ] **Step 1: Testes que falham.**
  - Monte uma tool de teste no próprio arquivo de teste: uma classe com `@Tool(name = "contar") String contar()`, que incrementa um `AtomicInteger`.
  - Monte o `ChatModel` mock e o `ChatClient.builder(chatModel)` com:
    - `defaultAdvisors(new LimiteIteracoesAdvisor(ToolCallingManager.builder().maxTotalToolCalls(10).build(), 5), MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().build()).order(ToolCallingAdvisor.DEFAULT_ORDER + 100).build())`;
    - `defaultToolCallbacks(ToolCallbacks.from(toolDeTeste))`;
    - cada chamada com `.toolContext(Map.of(RegistroTurno.CHAVE, registro))` e `.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "c1").param(RegistroTurno.CHAVE, registro))`.
  - Helpers:
    - `comTool(id, nome, argsJson)`: `AssistantMessage.builder().content("").toolCalls(List.of(new AssistantMessage.ToolCall(id, "function", nome, argsJson))).build()`, com finish `tool_use` e `new DefaultUsage(10, 5)`;
    - `texto(String)`: finish `end_turn`.
  - Se o ChatClient exigir `chatModel.getOptions()`, faça o stub com `AnthropicChatOptions.builder().build()`.

  Testes:
  - `paraNaQuintaChamadaSemExecutarAsTools`: sempre `comTool("t", "contar", "{}")`. Resultado: `verify(chatModel, times(5)).call(any(Prompt.class))`; o contador vale **4**; o finish é `FINISH_LIMITE`; há 5 iterações, a última com observação contendo `limite`, e cada uma com `toolsPedidas` `[contar]`.
  - `respostaSemToolNaoEhAlterada`: `texto("olá")` → 1 chamada, finish `end_turn`, 1 iteração com tokens 10/5.
  - `limiteDeToolsDoFrameworkEncerraOTurno`: a 1ª resposta tem 11 `ToolCall` de `contar` → finish `ToolCallLimitExceededException.FINISH_REASON` e 1 chamada ao modelo.
- [ ] **Step 2:** rodar. Esperado: FAIL.
- [ ] **Step 3: Implementar.**
- [ ] **Step 4:** `./mvnw test`, em verde.
- [ ] **Step 5: Commit** `feat: limite de chamadas à API por turno no loop do Spring AI`.

---

### Task 7: `AgenteSpringAi` e remoção do loop manual

**Files:**
- Create: `agente/AgenteSpringAi.java`; Test: `agente/AgenteSpringAiTest.java`
- Modify:
  - `agente/RespostaAgente.java` → `record RespostaAgente(String texto, List<IteracaoTurno> iteracoes)`;
  - `agente/FerramentasAgente.java` (remover `definicoes`, `executar`, os helpers antigos e o `JsonMapper`);
  - `simulador/SimuladorControllerTest.java`, `atendimento/AtendimentoServiceTest.java`;
  - `pom.xml` (remover o `anthropic-java` direto).
- Delete: `AgenteClaude`, `AnthropicConfig`, `ResultadoFerramenta`, `ChamadaFerramenta`, `IteracaoAgente`, `EnvioApi`, `RetornoApi`, `AgenteClaudeTest`, `FerramentasAgenteTest` (a cobertura passou para `ProdutoServiceTest` e `FerramentasAgenteToolsTest`).

**Interfaces:**
- Consumes: `FerramentasAgente` (T5), `RegistroTurno` / `IteracaoTurno` (T3), `LimiteIteracoesAdvisor` (T6), `AgenteProperties`, `ChatClient.Builder` e `ToolCallingManager` (T1).
- Produces:
  - `@Service AgenteSpringAi implements AgenteAtendimento`, com o construtor `AgenteSpringAi(ChatClient.Builder builder, ToolCallingManager manager, FerramentasAgente ferramentas, AgenteProperties props)`. Os testes passam `ChatClient.builder(chatModelMock)`.
  - `static final String MENSAGEM_FALLBACK`.

Montagem do ChatClient, uma vez no construtor:
- `defaultSystem`: o `prompts/sistema-atendimento.txt`, lido como hoje.
- `defaultOptions(AnthropicChatOptions.builder().model(props.modelo()).maxTokens(16000).effort(OutputConfig.Effort.of(props.effort())).build())`.
- `defaultToolCallbacks(ToolCallbacks.from(ferramentas))`.
- `defaultAdvisors`, nesta ordem:
  - `new LimiteIteracoesAdvisor(manager, props.maxIteracoes())`;
  - `MessageChatMemoryAdvisor.builder(memoria).order(ToolCallingAdvisor.DEFAULT_ORDER + 100).build()`;
  - `new SimpleLoggerAdvisor(ToolCallingAdvisor.DEFAULT_ORDER + 200)`.
- `memoria = MessageWindowChatMemory.builder().chatMemoryRepository(repositorio).build()`, com o `repositorio` (`InMemoryChatMemoryRepository`) num campo.

`responder(telefone, mensagem)`:
1. Mantém o lock por telefone e o `AtomicLong geracao`.
2. Tira o snapshot `List.copyOf(memoria.get(telefone))` e cria um `new RegistroTurno()`.
3. Chama `chatClient.prompt().user(mensagem).toolContext(Map.of(FerramentasAgente.CHAVE_TELEFONE, telefone, RegistroTurno.CHAVE, registro)).advisors(a -> a.param(ChatMemory.CONVERSATION_ID, telefone).param(RegistroTurno.CHAVE, registro)).call().chatResponse()`.
4. Trata o desfecho:

| Desfecho | Ação |
|---|---|
| `RuntimeException` | `log.warn("Falha no turno de {}.", telefone, e)` e `registro.observar("Falha: " + e.getMessage())`, depois restaura e devolve fallback |
| finish `FINISH_LIMITE` | restaura e devolve fallback |
| finish `ToolCallLimitExceededException.FINISH_REASON` | `registro.observar("Atingiu o limite de tools por turno; o turno foi interrompido.")`, depois restaura e devolve fallback |
| texto nulo ou em branco | `registro.observar("O modelo terminou sem texto.")`, depois restaura e devolve fallback |
| sucesso | se a geração mudou, `memoria.clear(telefone)`; devolve `RespostaAgente(texto, registro.iteracoes())` |

"Restaurar" significa: se a geração não mudou, `memoria.clear(telefone)` e, com o snapshot não vazio, `memoria.add(telefone, snapshot)`. Se mudou, só `clear`. O fallback devolve `RespostaAgente(MENSAGEM_FALLBACK, registro.iteracoes())`.

`reiniciarConversas()`: incrementa a geração e apaga tudo com `repositorio.findConversationIds()` + `deleteByConversationId`.

- [ ] **Step 1: Testes que falham** (`AgenteSpringAiTest`).
  - Usa os helpers da T6, `AgenteProperties("claude-haiku-5-5", "low", 5)`, `ToolCallingManager.builder().maxTotalToolCalls(10).build()`, as tools reais (`FerramentasAgente` sobre `ProdutoService` / `CarrinhoService` / `EstoqueService` / `ProdutoRepositoryEmMemoria` reais) e um `ArgumentCaptor<Prompt>`.
  - "Mensagens enviadas" são as `getInstructions()` sem a `SystemMessage`.

  Testes:
  - `toolSeguidaDeTextoRetornaTextoEIteracoes`: `consultar_produto {"termo":"camisa preta"}` e depois `"Temos sim!"`. Resultado: o texto; 2 iterações, a 1ª com finish `tool_use`, `toolsPedidas` `[ToolPedida("consultar_produto", "{\"termo\":\"camisa preta\"}")]` e tokens 10/5, e a 2ª com finish `end_turn`.
  - `segundaChamadaLevaOToolResultDaBusca`: o 2º prompt contém uma `ToolResponseMessage` com id `t1` e o `responseData` contendo `P001`.
  - `paraNoLimiteDeIteracoesComFallback`: P001 sempre adicionado (`{"produtoId":"P001","quantidade":1}`). Resultado: fallback, 5 chamadas, estoque de P001 igual a 8, 5 iterações.
  - `limiteDeToolsRetornaFallback`: 11 `ver_carrinho` na 1ª resposta. Resultado: fallback, observação contendo `limite de tools`.
  - `erroDaToolVoltaAoModelo`: `adicionar_ao_carrinho {"produtoId":"P001","quantidade":999}` e depois um texto. O texto chega, e o tool result do 2º prompt contém `Estoque insuficiente`.
  - `entradaComFormatoInvalidoVoltaAoModeloComoErro`: `{"produtoId":"P001","quantidade":"dois"}` e depois um texto. Sem exceção, o texto chega, e o estoque de P001 é 12.
  - `turnoComFalhaNaoAlteraMemoria`: `"olá"`, depois `thenThrow(new RuntimeException("falha simulada"))`, depois `"tudo certo"`. O 2º turno devolve fallback. O 3º prompt tem exatamente `[user "oi", assistant "olá", user "e agora?"]`.
  - `falhaDepoisDeToolNaoDeixaToolUseNaMemoria`: uma tool e depois `thenThrow`. No turno seguinte, o prompt não tem `AssistantMessage` com tool calls nem `ToolResponseMessage`.
  - `textoEmBrancoRetornaFallbackSemSujarMemoria`.
  - `memoriaSeparadaPorTelefone`.
  - `resetDuranteTurnoNaoRessuscitaConversa`: o `thenAnswer` chama `agente.reiniciarConversas()` e devolve `"olá"`. O turno seguinte tem 1 mensagem.
  - `memoriaGuardaToolsEntreTurnos`: o prompt do turno 2 contém a `ToolResponseMessage` do turno 1.
  - `janelaCheiaComecaEmMensagemDoCliente`: 5 turnos de tool + texto. No 6º prompt, a 1ª mensagem não-system é uma `UserMessage`, e há no máximo 21 mensagens não-system.
  - `requisicaoLevaModeloEffortEAsCincoTools`: as opções do 1º prompt são `AnthropicChatOptions` com modelo `claude-haiku-5-5` e `maxTokens` 16000; `getToolCallbacks()` tem 5 itens; a `SystemMessage` não está em branco.
- [ ] **Step 2:** `./mvnw test -Dtest=AgenteSpringAiTest`. Esperado: FAIL.
- [ ] **Step 3: Implementar e remover.**
  - Fazer as remoções e mudanças listadas acima.
  - No `SimuladorControllerTest`, usar `new RespostaAgente("olá", List.of(new IteracaoTurno(1, "tool_use", 10, 5, List.of(new ToolPedida("consultar_produto", "{}")), null)))` e verificar `$.iteracoes[0].finishReason` e `$.iteracoes[0].toolsPedidas[0].nome`.
  - Verificar que **não existe** `$.chamadas`.
- [ ] **Step 4:** `./mvnw test`, todos verdes, incluindo o `contextLoads`.
- [ ] **Step 5: Commit** `feat: agente com Spring AI (loop do framework) e remove o loop manual`.

---

### Task 8: Documentação e verificação final

**Files:** Modify `CLAUDE.md`, `docs/contexto.md`

- [ ] **Step 1: `CLAUDE.md`.**
  - Stack: o Spring AI 2.1 entra (tools `@Tool`, loop do `ToolCallingAdvisor`, memória `MessageChatMemoryAdvisor`). Saem as linhas sobre o SDK manual e as duas versões do Jackson.
  - Estrutura: `produto/ProdutoService`, `agente/FerramentasAgente` como adaptador, `RegistroTurno`, `LimiteIteracoesAdvisor` e `AgenteSpringAi`.
  - A regra das camadas: tools só delegam ao domínio.
  - O limite em camadas.
  - Logs: `SimpleLoggerAdvisor` em DEBUG.
  - A dica do "vou chamar um atendente" aponta para o `log.warn` do `AgenteSpringAi`.
- [ ] **Step 2: `docs/contexto.md`.**
  - Trocar menções a "sem Spring AI nesta fase" por "Spring AI 2.1 para function calling".
  - Na decisão em aberto 11, acrescentar `(demo: 5 chamadas à API por turno + 10 tools como rede de segurança; escalonamento para humano fica para a etapa de handoff)` sem fechá-la.
- [ ] **Step 3:** `./mvnw test`, em verde.
- [ ] **Step 4: Commit** `docs: Spring AI no CLAUDE.md e no contexto`.
- [ ] **Step 5: Roteiro manual** (é o usuário quem roda, com a chave do `.env`): `.\mvnw.cmd spring-boot:run` e abrir `http://localhost:8080`.
  - "tem camisa preta?": 1 tool e 2 iterações.
  - "quero a preta M": o painel mostra `adicionar_ao_carrinho` com a entrada, e o estoque cai. No turno seguinte, ver se o id foi reaproveitado sem nova busca.
  - "quero 999": o modelo explica que não há estoque.
  - "quero camisa preta M, calça 42 e um boné": deve passar.
  - Com o `SimpleLoggerAdvisor` em DEBUG, o resultado das tools e o JSON de cada rodada aparecem no terminal.
