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

O banco está desligado temporariamente via `spring.autoconfigure.exclude` (dados em memória); os testes rodam sem PostgreSQL e sem chave da Anthropic (o `AnthropicClient` é mockado nos testes; nenhum teste chama a API real).

Demo do simulador (precisa de `ANTHROPIC_API_KEY`):

```powershell
$env:ANTHROPIC_API_KEY="..."; .\mvnw.cmd spring-boot:run   # depois abrir http://localhost:8080
```

Modelo, effort e limite de iterações do agente ficam no bloco `agente:` do `application.yaml`.

Plano da demo: `docs/superpowers/plans/2026-11-20-demo-agente-atendimento.md` (spec em `docs/superpowers/specs/`).

## Estrutura da demo (pacotes sob `app.gestaoIAWpp`)

- `produto/`: `CatalogoMock` (8 produtos e estoque inicial em memória), `ProdutoRepository` + `ProdutoRepositoryEmMemoria` (busca por pontuação, tolerante a acento e pontuação), `EstoqueService` (baixa/devolução atômicas).
- `carrinho/`: `CarrinhoService`, um carrinho por telefone; adicionar baixa o estoque, remover devolve.
- `agente/`: `AgenteAtendimento` (a porta da IA), `AgenteClaude` (loop manual da Messages API com histórico por telefone), `FerramentasAgente` (definição das 5 tools e despacho; nunca lança exceção), prompt em `src/main/resources/prompts/sistema-atendimento.txt`.
- `atendimento/`: `AtendimentoService.processarMensagem(telefone, texto)`, entrada agnóstica de canal (o futuro webhook do WhatsApp chama o mesmo serviço).
- `simulador/`: endpoints REST `/api/simulador/*` e a página `src/main/resources/static/index.html`.

Duas versões do Jackson convivem: o SDK da Anthropic usa Jackson 2 internamente (a entrada das tools é lida com `JsonValue.convert(...)`); o resto do projeto usa o Jackson 3 do Boot 4 (`tools.jackson`). Não passe objetos do SDK para o `JsonMapper`.

## Stack

- Java 21, Spring Boot 4.2.0 (starters separados no estilo do Boot 4: `spring-boot-starter-webmvc`, `spring-boot-starter-flyway` e os `*-test` correspondentes)
- Spring AI fora nesta fase: o function calling é manual com o SDK oficial `com.anthropic:anthropic-java` (volta na etapa de RAG).
- PostgreSQL + Flyway para o schema. As migrations vão em `src/main/resources/db/migration`.
- Pacote base: `app.gestaoIAWpp`

## Arquitetura pretendida (de `docs/contexto.md`)

O sistema tem três camadas: chatbot (interface de conversa), agente de IA (executa ações via tools) e sistema de gestão (fonte da verdade sobre clientes, pedidos, inbox do atendente e handoff). Regras que valem para qualquer código novo:

- **O sistema de gestão precisa funcionar sem a IA.** A IA fica isolada atrás de uma interface (ex.: `AgenteAtendimento`), num monolito modular.
- Tudo em Java/Spring. Não criar serviço separado em Python.
- Preço e estoque sempre vêm de tools ou do banco, nunca do texto gerado pelo modelo. O carrinho é persistido no banco e manipulado via tools, não guardado só na memória da LLM.
- O bot nunca coleta dados de cartão, só a preferência de forma de pagamento. Um atendente humano valida o pedido inteiro antes do pagamento.
- Usar só a WhatsApp Cloud API oficial (nada de Baileys ou Evolution API). Pela política da Meta, o bot deve se limitar ao escopo da loja.
- Nesta fase não se usa Spring AI nem LangChain4j; quando um framework entrar, será só um deles.

O vocabulário de domínio é em português (pedido, carrinho, atendente, estados como `AGUARDANDO_VALIDACAO`). Mantenha esses nomes no código.
