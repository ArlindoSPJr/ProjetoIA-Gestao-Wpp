# Design: tools do agente por domínio (Strategy) sobre o Spring AI

Data: 2026-11-23. Substitui `2026-11-23-tools-strategy-design.md`, que foi escrito para o function calling
manual; a `main` já migrou para o Spring AI 2.1 e aquela branch (`feat/refatoração-tools`) não será integrada.

## Intenção

Organizar as tools do agente por domínio, com um ponto único que as descobre sozinho, e concentrar em `ai/`
tudo o que é do agente, incluindo os DTOs de saída. Para criar uma tool nova, basta escrever um `@Component`
num pacote de domínio; nada no agente muda.

## Estado atual (main, Spring AI)

- `agente/FerramentasAgente`: as 5 tools como métodos `@Tool`, só adaptadores de `ProdutoService` e
  `CarrinhoService`. O telefone vem do `ToolContext` (chave `CHAVE_TELEFONE`).
- `AgenteSpringAi` registra as tools com `ToolCallbacks.from(ferramentas)`; o Spring AI gera os schemas,
  despacha pelo nome e serializa o retorno. Não há `switch`.
- O LLM recebe os records de domínio (`ResultadoBusca`, `ProdutoResumo`, `DisponibilidadeProduto`,
  `CarrinhoResumo`, `ItemCarrinho`).

## Abordagens consideradas

- **A) Interface marcadora `AgentTool` + `ToolRegistry` (escolhida).** Os domínios implementam `AgentTool` e
  têm os métodos `@Tool`; o registry junta tudo em `ToolCallback[]`. O despacho continua no Spring AI.
  Idiomática e com pouco código próprio.
- B) `AgentTool` com `List<ToolCallback> callbacks()`: cada domínio repetiria `ToolCallbacks.from(this)` sem
  ganho hoje.
- C) `ToolCallback` escrito à mão por tool: reimplementa o framework. Descartada.

## Decisões

1. **Pacote `ai/`** recebe `AgenteAtendimento`, `AgenteSpringAi`, `AgenteProperties`,
   `LimiteIteracoesAdvisor`, `RegistroTurno`. O pacote `agente/` deixa de existir.
2. **`ai/AgentTool`**: interface marcadora (sem métodos). Não se chama `Tool` porque as classes que a
   implementam importam a anotação `@Tool` do Spring AI.
3. **`produto/ProdutoTool`** (`consultar_produto`, `consultar_estoque`, `@Order(1)`) e
   **`carrinho/CarrinhoTool`** (`adicionar_ao_carrinho`, `remover_do_carrinho`, `ver_carrinho`, `@Order(2)`):
   `@Component`, implementam `AgentTool`, métodos `@Tool` movidos de `FerramentasAgente` sem mudar nome,
   descrição, parâmetros nem `required`. Delegam aos services e mapeiam o retorno de domínio para o
   `*Response`.
4. **`ai/ToolRegistry`** (`@Component`): recebe `List<AgentTool>` (ordem do `@Order`), devolve
   `ToolCallback[] callbacks()` via `ToolCallbacks.from(...)` e falha no startup se dois nomes de tool se
   repetirem. `AgenteSpringAi` passa a usar `defaultToolCallbacks(registry.callbacks())`.
5. **`ai/ContextoTool`** (minha decisão): guarda `CHAVE_TELEFONE` e o `telefone(ToolContext)` que hoje são
   privados da `FerramentasAgente`, porque agora são usados pelo `AgenteSpringAi` e pelo `CarrinhoTool`.
6. **DTOs em `ai/dto/`, só `*Response`** (entradas continuam sendo os parâmetros dos métodos `@Tool`, para
   não mudar o schema que o LLM vê):
   - saída das tools: `ConsultarProdutoResponse` + `ProdutoResponse` (de `ResultadoBusca`/`ProdutoResumo`),
     `ConsultarEstoqueResponse` (de `DisponibilidadeProduto`), `CarrinhoResponse` + `ItemCarrinhoResponse`
     (de `CarrinhoResumo`/`ItemCarrinho`). Mesmos campos, mesma ordem, `mensagem` com
     `@JsonInclude(NON_NULL)`. O mapeamento fica nas classes `*Tool` (o `ai/dto` não importa o domínio).
   - saída do agente, renomeadas: `RespostaAgente` → `AgenteResponse`; `IteracaoTurno` →
     `IteracaoTurnoResponse` e `ToolPedida` → `ToolPedidaResponse` (minha decisão, pela mesma regra que você
     aprovou para os DTOs do agente). Os nomes dos componentes não mudam, então o JSON do simulador também
     não muda.
7. Os records de domínio (`ResultadoBusca` etc.) continuam existindo e sendo usados pelos services e pelo
   simulador; só deixam de ir direto para o LLM.

## Restrições

- Nada muda no que o LLM vê: nomes, descrições e JSON Schema de entrada das 5 tools, e o JSON de saída de cada
  uma, idênticos ao da `main`. Uma caracterização feita antes de mexer congela os dois.
- Regra de negócio inalterada (continua nos services).
- Os testes existentes continuam passando com as mesmas asserções (só setup, imports e nomes de tipo mudam).
- `CLAUDE.md`: atualizar a seção "Estrutura da demo" e a "Regra das camadas" para os novos nomes.

## Fora do escopo

Novas tools; mudar schemas; DTOs `*Request`; persistência; mudanças no loop, memória ou advisors.

## Integração

Branch `feat/tools-strategy-spring-ai`, criada da `main` local (que tem 22 commits do Spring AI ainda não
enviados). O PR para `origin/main` leva esses commits junto; o merge publica a migração para o Spring AI.
