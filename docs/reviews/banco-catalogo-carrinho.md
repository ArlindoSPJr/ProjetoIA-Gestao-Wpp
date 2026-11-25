# Review cega: banco-catalogo-carrinho

Spec: `docs/specs/2026-11-24-banco-catalogo-carrinho.md`. Diff: `git diff origin/main...HEAD`.

## Rodada 1: REPROVADO
Build ok, 127 testes (101 unit + 26 IT). Quatro achados IMPORTANTE, nenhum BLOQUEANTE:

| # | Achado | Resolução |
|---|---|---|
| 1 | A busca ignora valores de opção que só existem em variantes inativas, e a RN09 dizia "valor de opção do produto" | Humano escolheu ajustar a spec: RN09 passa a dizer "valor de opção de uma variante ativa". Código e teste já batiam (`3d628c3`) |
| 2 | Nenhum teste provava que variante de produto inativo não pode ser adicionada | Teste RN07 no `ProdutoRepositoryIT`, que falhava sem o filtro `p.ativo` (`9db691a`) |
| 3 | Reset concorrente com `adicionar` podia perder estoque | Carrinho travado com `PESSIMISTIC_WRITE` no reset e nas alterações de item; `adicionar` trava antes de baixar o estoque. IT de concorrência que falhava antes (estoque terminava em 11, não 12) (`b7aad3f`) |
| 4 | `/estado` com telefone em branco sem teste e com a mensagem errada | Mensagem `"Telefone é obrigatório."` e teste (`70216f0`) |

## Rodada 2: APROVADO
Build ok, 151 testes (123 unit + 28 IT com postgres:17), JaCoCo ok. Dependências conferidas com `dependency:get` e `gh api advisories` (vazio). Nenhum BLOQUEANTE nem IMPORTANTE.

Achados MENOR, deixados para depois:
- M1: o T01 não confere que são exatamente 10 tabelas.
- M2: não há teste de que o lock do agente é por `clienteId` (não por empresa).
- M3: o resumo do carrinho faz N+1 consultas (cerca de 7 com 1 item; perto de 2 s no Supabase a 150 ms por ida e volta).
- M4: o diagrama de classes não mostra `SimuladorController` → `ClienteService`/`ProdutoService`, `AtendimentoService.empresaDoNumero` e `ClienteService.buscar`.
- M5: `deveRetornar400_quandoTelefoneEmBranco` não verifica o argumento passado ao service.
- M6: testes redundantes (mesma partição) em `SimuladorControllerTest`, `AtendimentoServiceTest`, `ProdutoServiceTest` e `ToolsAgenteTest`.
- M7: o reset limpa a memória do agente antes de confirmar o número da demo.

Fora da spec, registrado pelo revisor:
- Travamento do carrinho (`travarDoCliente`/`travarDaEmpresa`), vindo da correção do achado 3.
- `userIdDoSimulador` normaliza dígitos e recusa telefone fora de 10 a 15 dígitos (evita 500 pelo CHECK da RN15).
- RLS da `flyway_schema_history` por callback `afterMigrate.sql`, porque o `ALTER` dentro de uma migration trava no lock do próprio Flyway.
- `docs/API-Meta-Especificacoes.md` no diff (pesquisa da API da Meta, recriada nesta branch).
