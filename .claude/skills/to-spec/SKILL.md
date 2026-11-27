---
name: to-spec
description: "Etapa de spec do dev-flow: transforma o grill da conversa numa spec em docs/specs/, sem nova entrevista, e para até o humano aprovar."
disable-model-invocation: true
---

Transforme o grill desta conversa numa spec do dev-flow. Esta skill **sintetiza**: as perguntas já foram feitas no `/grill-with-docs`. A fonte de verdade das regras da spec é a seção "1. Grill e spec" de [dev-flow](../dev-flow/SKILL.md); leia-a antes de começar.

## Passos

1. **Explore a área** do código que a feature toca, se ainda não explorou. Use o vocabulário do `GLOSSARY.md` e respeite os ADRs de `docs/adr/` dessa área.

2. **Confirme as seams com o humano.** Seam é o ponto onde a feature vai ser testada. Prefira as que já existem (um serviço do domínio, uma tool, um endpoint) e a mais alta possível; quanto menos seams, melhor, e o ideal é uma. Mostre a lista e espere a confirmação. Concluído quando o humano confirmou as seams.

3. **Escreva a spec** a partir de [spec-template.md](../dev-flow/spec-template.md), em `docs/specs/AAAA-MM-DD-<slug>.md`, com `Status: rascunho`. Cada linha da Matriz de testes usa uma seam confirmada no passo 2. Concluído quando toda seção do template está preenchida (ou com `N/A: <motivo>` onde o template permite) e "Perguntas em aberto" está vazia.

4. **Peça a aprovação.** Mostre o caminho da spec e pare até o humano escrever "aprovado". Com o "aprovado", mude o `Status` para `aprovada (AAAA-MM-DD)` e diga que o próximo passo é `/to-tickets`.

A spec é um arquivo no repositório. A issue pai e os labels ficam com o `/to-tickets`.
