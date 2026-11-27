---
name: to-tickets
description: Etapa de tickets do dev-flow: corta uma spec aprovada em tickets tracer-bullet com bloqueios e publica como sub-issues no GitHub, com aprovação do humano.
disable-model-invocation: true
---

Corte uma spec **aprovada** em tickets: fatias verticais (tracer bullets), cada uma com os tickets que a **bloqueiam**, publicadas como sub-issues no GitHub. A fonte de verdade das regras de corte, do template do ticket, das checagens, dos labels e dos comandos `gh` é a seção "2. Tickets" de [dev-flow](../dev-flow/SKILL.md); leia-a antes de começar.

## Passos

1. **Leia a spec.** Se o humano passou um caminho ou uma issue como argumento, leia esse; senão, a spec aprovada desta conversa. Confira `Status: aprovada (...)`. Spec em rascunho = pare e diga que falta o `/to-spec` ou o "aprovado".

2. **Explore o código** da área, se ainda não explorou, procurando **prefactor**: "deixe a mudança fácil, depois faça a mudança fácil". Use o vocabulário do `GLOSSARY.md` e respeite os ADRs de `docs/adr/`.

3. **Corte** seguindo "Como cortar" do dev-flow: fatia vertical, prefactor primeiro, expand–contract para refactor largo, ~500 linhas de lógica por ticket, bloqueios por arquivo compartilhado, versão de migration reservada. Concluído quando a checagem "Checagem antes de publicar" passa: cada linha da Matriz de testes está em exatamente um ticket.

4. **Mostre o corte ao humano**, numerado, com título, tamanho estimado, bloqueado por, linhas da matriz e o que o ticket entrega de ponta a ponta. Pergunte se a granularidade está certa, se os bloqueios são reais e se algum ticket deve ser juntado ou dividido. Itere até ele aprovar o corte.

5. **Publique** seguindo "Publicar" do dev-flow: labels, issue pai, uma sub-issue por ticket na ordem de dependência (bloqueadores primeiro) com o template do dev-flow e o label `aguardando-aprovacao`, vínculos nativos de sub-issue e de bloqueio, e a troca para `ready-for-agent` com o "aprovado" do humano (ou direto, se for um ticket só). Concluído quando os vínculos foram confirmados pelos comandos de verificação.

A issue pai fica como está: nada de fechá-la ou editá-la.
