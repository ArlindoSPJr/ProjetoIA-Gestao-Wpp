# Política de testes

Vale para todo agente que cria, altera ou avalia testes. Os comandos ficam no CLAUDE.md.

## Quem faz o quê
| Papel | Responsabilidade |
|---|---|
| Spec (humano + grill-me) | Define a **matriz de testes**: quais caminhos precisam de teste, antes de existir código. |
| Implementador (subagent-driven-development + TDD) | Escreve os testes **antes** do código, a partir da matriz, um caminho por vez. |
| blind-reviewer | **Não escreve testes.** Verifica se cada caminho da matriz tem teste e se os testes provam algo. |
| Sonar-fix | Só cria teste para um **branch descoberto** que ele identificar no relatório de cobertura, seguindo esta política. |

## Regra principal: cobertura vem de caminhos diferentes, não de mais casos iguais
Antes de escrever um teste, responda: **qual caminho este teste exercita que nenhum outro teste já exercita?** Se a resposta for "nenhum", não escreva o teste.

Um caminho novo é um destes:
- **Partição de equivalência nova:** uma classe de entrada que o código trata de forma diferente (válido, inválido, vazio, nulo).
- **Valor limite:** exatamente no limite, logo abaixo e logo acima (ex.: 0, 1, máx, máx+1).
- **Branch de decisão:** cada condição avaliada como verdadeira **e** falsa, incluindo cada parte de condições compostas (`a && b`).
- **Caminho de erro:** cada exceção, validação recusada, timeout ou falha de dependência que o código trata.
- **Transição de estado:** cada transição válida e as inválidas relevantes.
- **Regra de negócio ou critério de aceite** ainda sem teste.

Não contam como caminho novo:
- Outro valor da **mesma** partição (testar idade 25, 30 e 40 quando a regra é "≥ 18").
- O mesmo cenário com outro nome, ou uma asserção repetida.
- Getters/setters, construtores triviais ou código do framework.

## Como medir se o teste vale a pena
- Use **cobertura de branch**, não só de linha.
- Todo teste novo precisa **cobrir um branch ainda não coberto** ou **matar um mutante sobrevivente** (mutation testing). Se não fizer nenhum dos dois, remova.
- Testes parametrizados servem para limites e partições diferentes, não para repetir a mesma partição com vários valores.

## Qualidade de cada teste
- Nome descreve o comportamento: `deve<Resultado>_quando<Condição>`.
- Uma regra por teste, com asserção sobre o **resultado observável** (retorno, estado, efeito), não sobre detalhes internos.
- Não mockar a própria unidade testada nem a lógica que está sendo verificada. Mock só nas fronteiras (banco, rede, relógio, fila).
- Teste sem asserção, ou que passa mesmo com o código quebrado, é defeito.
- Determinístico: sem depender de horário real, ordem de execução, rede externa ou aleatoriedade sem semente.

## Níveis
- **Unitário:** regra de negócio e lógica de decisão (a maior parte dos caminhos).
- **Integração:** persistência, HTTP, mensageria, configuração; um teste por contrato, não por regra.
- **E2E:** só os fluxos principais dos fluxogramas da spec, caminho feliz e um erro crítico. Não repetem os caminhos já cobertos por unitários.
