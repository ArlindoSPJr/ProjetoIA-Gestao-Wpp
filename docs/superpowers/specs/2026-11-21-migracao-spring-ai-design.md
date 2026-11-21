# Spec: migração do agente para Spring AI 2.1

## Por quê

A demo tem o loop de function calling escrito à mão (`AgenteClaude`, SDK `anthropic-java`). O objetivo foi aprender o loop, e isso está cumprido. O próximo passo é usar um framework, pensando em produção e na entrada futura do LangGraph4j (`langgraph4j-spring-ai`). O `docs/contexto.md` já previa Spring AI + pgvector. A regra de usar um framework só continua valendo: nada de LangChain4j.

## Decisões (tomadas com o usuário em 2026-11-21)

| Tema | Decisão |
|---|---|
| Framework | Spring AI **2.1.0** (BOM). Ele é compilado contra o Boot 4.2.0 e o Jackson 3, e usa o `anthropic-java-core` 3.8.0. O LangChain4j ficou de fora porque os starters Spring dele estão em beta e foram feitos para o Boot 3.5. |
| Loop | **Automático**, feito pelo `ToolCallingAdvisor` do ChatClient. Nosso código não repete chamadas à API. |
| Tools | Métodos `@Tool` / `@ToolParam` no `FerramentasAgente`, que passa a ser só um **adaptador**: recebe a entrada do modelo, lê o telefone do `ToolContext` e delega ao service do domínio. A regra de negócio fica no domínio: a busca, o ranking e a disponibilidade ficam num novo `ProdutoService` (`produto/`), e o carrinho no `CarrinhoService`. O framework gera o schema, converte a entrada e despacha. |
| Nomes | As tools mantêm os nomes snake_case de hoje. Os parâmetros passam a ser camelCase (`termo`, `produtoId`, `quantidade`), porque o schema é gerado dos nomes dos parâmetros Java. |
| Retornos | **Records** do domínio em vez de `Map` (`ResultadoBusca`, `ProdutoResumo` e `DisponibilidadeProduto`, em `produto/`). O JSON que o modelo recebe mantém os campos de hoje. |
| Erros de tool | Os métodos **lançam exceção**. O framework (`DefaultToolExecutionExceptionProcessor`) devolve a mensagem como resultado da tool, e o modelo explica ao cliente. A API não recebe mais `is_error`. |
| Strict | **Sem `strict`**, porque o Spring AI 2.1 não o envia para a Anthropic. Um erro de formato na entrada vira erro para o modelo (custa uma rodada) e nunca derruba o turno. A validação de conteúdo (quantidade ≥ 1, id existente, estoque) continua no Java. |
| Limite | Em camadas: **5 chamadas à API (iterações) por turno**, num advisor próprio que estende o `ToolCallingAdvisor`, porque o framework não tem limite de rodadas. Como rede de segurança, **10 tools por turno** (`spring.ai.tools.limits.max-total-tool-calls`, comportamento `THROW`, nativo). Ao estourar qualquer um, o turno devolve a mensagem de fallback. Quando o LangGraph4j entrar, o `recursionLimit` dele substitui o advisor. |
| Memória | `MessageChatMemoryAdvisor` **dentro** do loop (order maior que o do advisor de tools). Assim ele guarda também os `tool_use` e `tool_result`, e o modelo continua lembrando os ids dos produtos entre os turnos. `MessageWindowChatMemory` com a **janela padrão de 20 mensagens**: o corte sempre começa numa mensagem do cliente, sem separar um `tool_use` do seu `tool_result`. |
| Falha | Um turno que falha (exceção, limite, texto vazio) **não deixa rastro na memória**: a memória do telefone volta ao snapshot de antes do turno. Um reset durante o turno não ressuscita a conversa. São as mesmas garantias da versão manual. |
| Logs | **Só os do framework**: o `SimpleLoggerAdvisor`, dentro do loop, mostra em DEBUG a requisição e a resposta de cada rodada. Nossas classes não logam rodadas nem tools. Fica só o `log.warn` de falha do turno. |
| Página | A seção "Iterações do último turno" continua: rodada, finish reason, tokens, **tools pedidas com a entrada** (o advisor as vê na resposta do modelo) e observação. Ela não mostra o resultado da tool, que fica no log DEBUG, nem o JSON de envio e retorno. Não há decorator de tools: não vale escrever código só para o painel. O contrato HTTP passa a ser `{texto, iteracoes}` (sai o campo `chamadas`). |
| Loop manual | Removido. Ele fica no git, na branch `feature/logs-iteracoes`, como referência de estudo. |

## Fatos do Spring AI 2.1.0 que o desenho usa (verificados no código-fonte)

- `AnthropicChatModel.call()` não faz loop. O `ToolCallingAdvisor` faz `do { … } while (isToolCall)` e tem os ganchos protegidos `doInitializeLoop`, `doBeforeCall`, `doAfterCall`, `doFinalizeLoop` e `doGetNextInstructionsForToolCall`. O `isToolCall` é avaliado (por `chatResponse.hasToolCalls()`) **sobre a resposta já devolvida por `doAfterCall`**. O `ToolCallAdvisor` está deprecated: estender o `ToolCallingAdvisor`, cujo construtor protegido é `(ToolCallingManager, ToolExecutionEligibilityChecker, int advisorOrder, boolean conversationHistoryEnabled)`.
- O ChatClient só registra sozinho um `ToolCallingAdvisor` se não houver nenhum `ToolAdvisor` na cadeia. Com um advisor próprio, nós passamos o `ToolCallingManager` (o bean autoconfigurado aplica `spring.ai.tools.limits.*`) e `conversationHistoryEnabled = false`, porque a memória fica downstream.
- O `MessageChatMemoryAdvisor.before()` grava a mensagem nova **antes** da chamada, e o `after()` grava a resposta de cada rodada. Por isso, um turno com falha deixaria lixo, até um `tool_use` sem `tool_result`, e a conversa seguinte quebraria com erro 400. Daí o snapshot e a restauração.
- Quando o limite de tools estoura, o advisor devolve uma `Generation` com `finishReason = ToolCallLimitExceededException.FINISH_REASON` (`"toolCallLimitExceeded"`).
- O `MethodToolCallback` embrulha tanto a falha de conversão da entrada quanto a exceção do método em `ToolExecutionException`. O processador devolve a mensagem da causa, ou um texto padrão se ela for nula.
- A chave da conversa é `ChatMemory.CONVERSATION_ID` (`"chat_memory_conversation_id"`).
- O usage chega por rodada em `doAfterCall`.
- O `AnthropicChatOptions.builder()` tem `model`, `maxTokens` e `effort(OutputConfig.Effort)`.
