# Contexto do projeto: Plataforma de atendimento e gestão de pedidos via WhatsApp com agente de IA

## Objetivo
Projeto de estudos (foco principal: aprender), com potencial de virar produto para pequenas e médias empresas que vendem pelo WhatsApp.
Dor resolvida: muitas conversas e clientes geram falta de rastreabilidade de pedidos e registros de compra.
Também serve como portfólio para vaga júnior backend Java.

## Posicionamento
Não é "só um chatbot". São três camadas:
1. Chatbot: interface de conversa (responde dúvidas)
2. Agente de IA: executa ações via ferramentas (consulta produtos, monta carrinho, extrai pedido)
3. Sistema de gestão: fonte da verdade (clientes, pedidos, inbox do atendente, handoff, histórico)
Teste: removendo a IA, o sistema de gestão continua funcional. O valor principal é a rastreabilidade.
Descrição curta: "sistema que transforma conversas de WhatsApp em pedidos registrados, com a IA como interface de entrada".

## Escopo do MVP (fluxo de IA)
- Consultar produto específico
- Realizar pedido (reaproveitando produtos já consultados e/ou buscando produtos dentro do fluxo)
- Tirar dúvidas sobre a loja (trocas, frete, horários) via documento enviado pela loja (RAG)
- Human-in-the-loop: o atendente valida o pedido antes do pagamento
- Fora do escopo por enquanto: cadastros no sistema de gestão (clientes, produtos etc.)

## Fluxo de pedido (definido pelo usuário)
1. Iniciar pedido
2. Verificar produtos já mencionados na conversa e se o cliente quer adicionar outros
3. Consultar estoque dos produtos solicitados
4. Cliente finaliza o pedido
5. Cliente informa dados de entrega (a definir)
6. Cliente informa forma de pagamento
7. Humano entra no loop: valida e envia link de pagamento/Pix
8. Sistema identifica venda concluída e dispara ações seguintes (fora do escopo)

## Decisões tomadas
- Stack: tudo em Java + Spring (sem serviço separado em Python)

## Propostas (ainda não confirmadas)
- Spring AI como camada de IA (alternativa: LangChain4j; não usar os dois)
- Máquina de estados própria no início; LangGraph4j (orquestração em grafo, integra com Spring AI) como evolução opcional
- Duas máquinas de estado separadas:
  - Conversa: BOT → AGUARDANDO_HUMANO → HUMANO → ENCERRADA
  - Pedido: RASCUNHO → AGUARDANDO_VALIDACAO → AGUARDANDO_PAGAMENTO → CONCLUIDO (+ CANCELADO/EXPIRADO)
- Carrinho persistido no banco, manipulado pela LLM via tools (adicionar_item, remover_item, ver_carrinho), nunca só na memória da LLM
- Preço e estoque sempre vêm de tool, nunca do modelo
- Produtos consultados são registrados como "produtos de interesse" (com ID) e oferecidos ao iniciar o pedido; preço e estoque são revalidados ao adicionar
- Limite técnico de chamadas de ferramenta por turno (ex.: 5) para evitar loop do agente
- O humano valida o pedido inteiro (itens, entrega, pagamento), não só o pagamento
- O bot só coleta a preferência de pagamento, nunca dados de cartão
- Isolar a IA atrás de uma interface (ex.: AgenteAtendimento): monolito modular

## Decisões em aberto (com opções levantadas)
1. Mensagem inicial: A) menu fixo; B) conversa livre; C) híbrido (menu só se a mensagem for saudação pura); D) botões só em momentos de decisão. Avaliar unir "Fazer pedido" e "Buscar produto".
2. Produtos têm variações (tamanho/cor) com estoque separado?
3. Modelo do fluxo de pedido: linear rígido / linear com desvio / preenchimento de campos (slot filling)
4. Entrega: delivery, retirada ou ambos; quais dados coletar
5. Cálculo de frete: fixo, tabela por região, automático por CEP ou informado pelo atendente
6. Formas de pagamento; link/Pix enviado manualmente ou gerado pelo sistema
7. Confirmação da venda: A) ação explícita no painel; B) webhook de provedor de pagamento (Mercado Pago, Asaas, Efí); C) inferência da LLM (só como sinal, nunca confirmação)
8. Após a venda, a conversa volta ao bot? Quem responde enquanto o pedido aguarda validação?
9. Quando reservar estoque: ao adicionar ao carrinho, ao finalizar ou após o pagamento
10. Cliente pode editar/cancelar pedido pelo bot após finalizar?
11. Loop de consultas do cliente: sem limite / limite fixo / sem limite rígido com escalonamento para humano após N falhas + timeout de inatividade (demo: 5 chamadas à API por turno + 10 tools como rede de segurança; escalonamento para humano fica para a etapa de handoff)

## Stack sugerida
Spring Boot, Spring AI, PostgreSQL + pgvector, Redis (debounce/estado), RabbitMQ (opcional), WhatsApp Cloud API oficial (número de teste gratuito), React/Next.js no painel, WebSocket/SSE, Spring Security + JWT, Docker, ngrok, Langfuse (observabilidade de LLM). Evitar libs não oficiais de WhatsApp (Baileys, Evolution API) em produção: risco de banimento.
Atenção: Spring AI 2.x exige Spring Boot 4; Spring AI 1.1.x funciona com Boot 3.5.

## Trilha de estudos (ordem)
1. Base Spring (JPA, Flyway, Docker, testes)
2. WhatsApp Cloud API (webhooks, HMAC, janela de 24h, templates)
3. Assíncrono e confiabilidade (filas, idempotência, debounce, retry)
4. Fundamentos de LLM e Spring AI
5. Memória de conversa (resumo, prompt caching)
6. RAG (embeddings, chunking, pgvector)
7. Tool calling
8. Saída estruturada (intenção, extração de pedido)
9. Máquina de estados e handoff
10. Painel web em tempo real
11. Guardrails, prompt injection, evals, observabilidade
12. Produção (LGPD, multi-tenancy, deploy, transcrição de áudio)

## Riscos e custos
- WhatsApp: cobrança por mensagem; mensagens de serviço iniciadas pelo cliente, dentro da janela de 24h, são gratuitas; templates fora da janela são pagos (aprox. R$0,04–0,05 utility, R$0,31–0,38 marketing em 2026)
- Política da Meta (desde 15/01/2026): proíbe chatbots de IA de propósito geral; IA de atendimento de negócio é permitida, então o bot deve se restringir ao escopo da loja
- Custo de LLM cresce com o tamanho do contexto: usar modelo pequeno, resumo de histórico e cache
- Alucinação, prompt injection, latência, LGPD
- Mercado concorrido (Blip Go: R$299/mês com agente de IA, funil Kanban, 100 disparos e conversas ilimitadas; também Zenvia, Kommo, respond.io)
- Diferencial possível: foco em registro de pedidos a partir de conversas + nicho (ex.: distribuidoras B2B recebendo pedidos por texto/áudio, ou pequenas lojas); variação "copiloto do atendente" (a IA não fala com o cliente, só extrai pedidos e sugere respostas)

## Próximo passo
Responder às decisões em aberto e então montar o fluxograma completo (dois diagramas de estado + fluxo por intenção).