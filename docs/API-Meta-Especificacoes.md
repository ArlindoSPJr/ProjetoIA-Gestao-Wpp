# API da Meta (WhatsApp Cloud API): especificações

Pesquisa feita em 22/11/2026 nas páginas oficiais da Meta. As páginas foram lidas por um extrator de texto (WebFetch), não diretamente no navegador, então números e nomes de campo devem ser conferidos nas URLs antes de virarem código. O que não foi confirmado numa página oficial está marcado como "(não confirmado na documentação)". As URLs consultadas estão na seção 9.

---

## 1. Visão geral e formas de comunicação

**Fontes:** `.../whatsapp/about-the-platform.md`, `.../whatsapp/access-tokens.md`, `.../embedded-signup/overview.md`, `.../whatsapp/get-started.md`. O prefixo comum é `https://developers.facebook.com/documentation/business-messaging`.

### Cloud API vs On-Premises
- A documentação atual cobre só a Cloud API, hospedada pela Meta e acessada pela Graph API.
- A descontinuação da On-Premises API não aparece em nenhuma página lida (não confirmado na documentação). Para o projeto isso não importa: usar só a Cloud API.

### Graph API e versão
- O endpoint base é `https://graph.facebook.com/{versao}/...`.
- Os exemplos mais recentes da página de envio usam **v27.0**. Outras páginas usam v21.0 ou v23.0 nos exemplos e tratam a versão como opcional (`<API_VERSION>`).
- A política de versionamento e de fim de vida das versões não está nas páginas lidas (não confirmado na documentação). Deixar a versão configurável (`whatsapp.graph-version`) e fixar uma explicitamente.
- Os webhooks mudam de shape por versão. Exemplo: a partir da v24.0 o objeto `conversation` some dos statuses, exceto em conversas de free entry point (ver seção 4).

### Entidades
A Meta está separando o WABA em duas coisas: a **WhatsApp account (WAAC)**, que guarda números e identidade, e a **Messaging account**, que guarda templates, cobrança e assinaturas de webhook. O `waba_id` passa a se referir ao ID da Messaging account, sem mudança de valor ou formato. Essa mudança está em beta, segundo a página do Embedded Signup. Para o código, `waba_id` continua sendo a chave.

| Entidade | O que é |
|---|---|
| Business portfolio (antes Business Manager) | Contêiner de WABA e Messaging accounts. Obrigatório. Pode ser verificado, o que afeta limites. Os **messaging limits são calculados nele**. |
| WABA (`entry[].id` no webhook) | Presença do negócio no WhatsApp. Guarda os números, o display name e os usernames. |
| `phone_number_id` | ID do número comercial. É o ID usado nas URLs de envio e vem em `metadata.phone_number_id`. Diferente do `display_phone_number`. |
| App | App do tipo Business, associado ao portfolio. Guarda o app secret e o verify token. |
| System User | Usuário de sistema criado no Business Settings, usado para tokens de longa duração. |

### Tokens (`.../whatsapp/access-tokens.md`)
- **User access token**: expira em poucas horas. Indicado só para o primeiro teste.
- **System user access token**: de longa duração, para quem acessa os próprios dados.
  - Gerado em Business Settings > System Users > +Add (Admin ou Employee).
  - Atribui-se o app com a permissão "Manage app", clica-se em "Generate token", escolhe-se o app e a expiração, e concedem-se as permissões `business_management`, `whatsapp_business_management` e `whatsapp_business_messaging`.
  - O system user precisa de acesso Partial ou Full tanto à WhatsApp account quanto à Messaging account.
  - A expiração é a escolhida na geração. A página não diz um prazo padrão.
- **Business Integration System User token** (para Tech Providers e Solution Partners): vem do Embedded Signup e é **escopado a um cliente onboardado**. É o token do nosso caso multi-tenant, um por empresa cliente.
- **Token temporário** do painel (API Setup > Generate access token): "expira rapidamente e não serve para desenvolvimento". A duração não é dada.
- Tratar o token como string opaca, em campo de tamanho variável.
- Envio: `Authorization: Bearer <token>`.

### Embedded Signup (`.../embedded-signup/overview.md`, `.../implementation.md`)
- É o fluxo de cadastro embutido (Facebook Login for Business + JS SDK, desktop e mobile) pelo qual o cliente cria ou vincula WABA e número.
- **Papéis:** o Tech Provider usa só business tokens e o cliente cadastra a própria forma de pagamento. O Solution Partner exige linha de crédito. Este projeto é um Tech Provider.
- **Fluxo:**
  1. O cliente abre o Embedded Signup no nosso portal.
  2. Autentica na Meta e aceita os termos.
  3. Escolhe ou cria o portfolio e o WABA.
  4. Informa e verifica o número e define o display name.
  5. O callback `FB.login` devolve `response.authResponse.code`, e a mensagem de sessão `WA_EMBEDDED_SIGNUP` entrega `waba_id`, `phone_number_id` e `business_id`.
- **O code vale 5 minutos.**
- **Requisitos:** App Review com acesso avançado para `whatsapp_business_management` e `whatsapp_business_messaging`. Todas as chamadas pós-signup são server-to-server.
- **Limite de onboarding:** por padrão 10 novos WABAs por 7 dias rolantes. Com Business Verification, App Review e Access Verification, sobe para 200.
- **Versões:** "Embedded Signup v2 e v3 serão descontinuadas em 15/10/2026". Já passou, então usar a **v4**.
- **Webhook:** assinar `account_update`, que dispara quando o cliente conclui o fluxo.

Payload da mensagem de sessão (conclusão):
```json
{
  "data": {
    "phone_number_id": "<ID_DO_NUMERO>",
    "waba_id": "<WABA_ID>",
    "business_id": "<ID_DO_PORTFOLIO>",
    "dataset_ids": []
  },
  "type": "WA_EMBEDDED_SIGNUP",
  "event": "FINISH"
}
```
Outros eventos: `CANCEL` (com `current_step`), `ERROR` (com `error_message`, `error_code`, `session_id`) e `FINISH_WHATSAPP_BUSINESS_APP_ONBOARDING`.

### Passos de onboarding no servidor
Fonte: `.../embedded-signup/onboarding-customers-as-a-tech-provider.md`. A ordem é:

1. **Trocar o code pelo business token:**
```bash
curl --get 'https://graph.facebook.com/v21.0/oauth/access_token' \
  -d 'client_id=<APP_ID>' -d 'client_secret=<APP_SECRET>' -d 'code=<CODE>'
# resposta: { "access_token": "...", "token_type": "..." }
```
2. **Assinar o app no WABA do cliente:**
```bash
curl -X POST 'https://graph.facebook.com/<VERSAO>/<WABA_ID>/subscribed_apps' \
  -H 'Authorization: Bearer <BUSINESS_TOKEN>'
# resposta: { "success": true }
```
3. **Registrar o número** (PIN de 6 dígitos, que vira o PIN de verificação em duas etapas):
```bash
curl 'https://graph.facebook.com/v21.0/<PHONE_NUMBER_ID>/register' \
  -H 'Content-Type: application/json' -H 'Authorization: Bearer <BUSINESS_TOKEN>' \
  -d '{ "messaging_product": "whatsapp", "pin": "<PIN_6_DIGITOS>" }'
```
4. Enviar mensagem de teste (opcional).
5. O cliente cadastra o meio de pagamento no WhatsApp Manager.

---

## 2. Webhooks

**Fontes:** `.../webhooks/overview.md`, `.../webhooks/create-webhook-endpoint.md`, `https://developers.facebook.com/docs/whatsapp/cloud-api/guides/set-up-webhooks`.

### Configuração
- No App Dashboard, em WhatsApp > Configuration, informa-se a callback URL e o verify token e assinam-se os campos. No caso de uso "Connect with customers through WhatsApp", o caminho é Use cases > Customize > Configuration.
- As permissões são `whatsapp_business_messaging` para os campos `messages` e `calls`, e `whatsapp_business_management` para os demais.
- O app precisa estar em modo **Live**. Alguns webhooks não são enviados em modo Dev.
- É possível **sobrescrever a callback URL** por Messaging account ou por número, e parceiros podem ter endpoint por cliente onboardado. Os detalhes estão em `.../webhooks/override.md`, que não foi lida (não confirmado na documentação).
- Campos assináveis relevantes ao projeto: `messages` (mensagens recebidas e statuses), `message_template_status_update`, `message_template_quality_update`, `phone_number_quality_update`, `account_update`, `account_alerts`, `business_capability_update`, `smb_message_echoes`, `history`, `calls`, `user_preferences` e `business_username_updates`.

### Verificação GET
A Meta envia:
```
GET <CALLBACK_URL>?hub.mode=subscribe&hub.challenge=<STRING_ALEATORIA>&hub.verify_token=<SEU_TOKEN>
```
- Comparar `hub.verify_token` com o valor guardado no servidor.
- Se bater, responder **HTTP 200 com o corpo igual a `hub.challenge`** (texto puro).
- Se não bater, responder 4xx.
- A Meta só começa a enviar eventos depois de receber 200 com o challenge correto.
- A verificação roda ao salvar ou editar a callback URL ou o verify token.

### Assinatura do POST
Cada POST traz o header `X-Hub-Signature-256: sha256=<HASH>`.
- Calcular HMAC-SHA256 do **corpo bruto** com o **app secret** como chave e comparar com o valor após `sha256=`. Se não bater, responder não-200.
- A documentação não diz "use comparação em tempo constante" nem "use o corpo bruto antes de parsear". São boas práticas, não texto oficial (não confirmado na documentação). Em Java: `Mac.getInstance("HmacSHA256")` sobre os bytes crus e `MessageDigest.isEqual` para comparar. Ler o `@RequestBody byte[]` ou `String` sem re-serializar.
- **Em multi-tenant com um app só (Tech Provider), o app secret é um só para todos os clientes**, o que simplifica a validação. Com `subscribed_apps` e callback override por cliente, a assinatura continua sendo a do app (não confirmado na documentação).
- Alternativa de segurança: mTLS (a Meta recomenda mTLS em vez de allowlist de IPs, porque os IPs mudam). A documentação de mTLS não foi lida.

### Retry, duplicatas, tamanho
- Qualquer resposta diferente de 200 ou falha de entrega gera retry "com frequência decrescente" por **até 7 dias**.
- Os retries vão para todos os apps assinados, então **podem chegar duplicatas** e o handler deve ser idempotente. A chave natural é o `wamid`.
- Cada POST pode trazer **até 1000 updates em lote**, então processar cada item do array individualmente.
- O payload pode ter até **3 MB**.
- **Não existe API para buscar webhooks históricos.** Persistir o payload bruto ao receber.
- O prazo exato de "responder rápido" (timeout) não está nas páginas lidas (não confirmado na documentação). A regra prática é responder 200 imediatamente e processar de forma assíncrona.

### Assinar o app num WABA (`subscribed_apps`)
Ver o passo 2 na seção 1: `POST /{waba_id}/subscribed_apps` com o business token devolve `{"success": true}`. O GET e o DELETE do mesmo endpoint não estão nas páginas lidas (não confirmado na documentação).

---

## 3. Payloads recebidos

**Fontes:** `.../webhooks/reference/messages.md` e `.../webhooks/reference/messages/<tipo>.md`.

### Envelope comum
```json
{
  "object": "whatsapp_business_account",
  "entry": [{
    "id": "102290129340398",
    "changes": [{
      "field": "messages",
      "value": {
        "messaging_product": "whatsapp",
        "metadata": { "display_phone_number": "15550783881", "phone_number_id": "106540352242922" },
        "contacts": [{
          "profile": { "name": "Sheena Nelson" },
          "wa_id": "16505551234",
          "user_id": "US.13491208655302741918"
        }],
        "messages": [ /* ver por tipo */ ]
      }
    }]
  }]
}
```
`entry[].id` é o WABA. O envelope traz `messages` OU `statuses` (ou `errors` no nível de `value`).

### Identidade do usuário: BSUID e usernames
Fonte: `.../business-scoped-user-ids.md` (página longa, só os primeiros 100 mil de 190 mil caracteres foram lidos).

- **Cronologia segundo a página:** desde abril/2026 a Meta envia BSUIDs nos webhooks. A reserva de usernames de negócio começou em 29/06/2026. Desde julho/2026 as APIs de envio aceitam BSUID como destinatário. Os usernames de usuários vêm sendo liberados gradualmente em 2026.
- **Formato:** código ISO do país + ponto + até 128 caracteres alfanuméricos. Exemplo: `US.13491208655302741918`. Deve ser usado inteiro.
- **Escopo:** é único por par (portfolio, usuário). Muda se o usuário trocar de número, e nesse caso chega uma mensagem `system`.
- **Em mensagens recebidas:**
  - `contacts[].user_id` e `messages[].from_user_id` vêm **sempre**.
  - `wa_id` e `from` vêm sempre se o usuário não tem username. Se tem username, vêm só se o telefone estiver disponível.
  - `profile.username` vem quando existe.
  - `parent_user_id` e `from_parent_user_id` vêm só se parent BSUIDs estiverem habilitados (formato `US.ENT.<digitos>`).
  - `identity_key_hash` só aparece se a checagem de troca de identidade estiver habilitada.
- **Quando o telefone é omitido (usuário com username):** o número só vem se, nos últimos 30 dias, por número comercial, você mandou mensagem ou ligou, o usuário escreveu ou ligou, ou ele está no contact book da Meta.
- **Regra para integrações:** tratar `user_id`/`from_user_id` como identificador primário. Aceitar `wa_id`/`from` ausentes. Armazenar `username` e `parent_user_id` quando houver. Tratar as mensagens `system` de troca de número e de BSUID.

Mensagem `system`, para ligar identidades antigas e novas:
```json
{
  "id": "wamid.HBgLMTk4MzU1NTE5NzQVAgASGAoxMTgyMDg2MjY3AA==",
  "timestamp": "1750269342",
  "system": {
    "body": "User changed from US.10230984751029384756 to US.13491208655302741918",
    "type": "user_changed_user_id",
    "user_id": "US.13491208655302741918",
    "previous_user_id": "US.10230984751029384756"
  },
  "type": "system"
}
```
`user_changed_number` traz também `from` e `system.wa_id`. Esses payloads não têm `contacts`.

### Por tipo (objeto dentro de `messages[]`)
Todos têm também `from`, `from_user_id`, `id` (wamid), `timestamp` (string Unix) e `type`.

**text**
```json
{ "from":"16505551234","from_user_id":"US.1349...","id":"wamid.HBg...","timestamp":"1749416383","type":"text","text":{"body":"Does it come in another color?"} }
```

**image**
```json
"image": {
  "caption": "Taj Mahal",
  "mime_type": "image/jpeg",
  "sha256": "SfInY0gGKTsJlUWbwxC1k+FAD0FZHvzwfpvO0zX0GUI=",
  "id": "1003383421387256",
  "url": "https://lookaside.fbsbx.com/whatsapp_business/attachments/?mid=133..."
}
```
`image.id` serve para `GET /{media-id}`. O campo `url` é liberado gradualmente desde 12/11/2025 e pode não vir, então não depender dele.

**audio**: `{"mime_type":"audio/ogg; codecs=opus","sha256":"...","id":"1908647269898587","voice":true}`. `voice:true` indica gravação de voz.

**video**: `{"caption":"Timelapse of growth","mime_type":"video/mp4","sha256":"...","id":"731675419373506"}`.

**document**: `{"caption":"my receipt","filename":"receipt.pdf","mime_type":"application/pdf","sha256":"...","id":"622684793477189"}`.

**sticker**: `{"mime_type":"image/webp","sha256":"...","id":"1908647269898587","url":"...","animated":true}`.

**location**
```json
"location": { "address":"101 Forest Ave, Palo Alto, CA 94301","latitude":37.44221496582,"longitude":-122.16165924072,"name":"Philz Coffee","url":"https://philzcoffee.com/" }
```
`url` costuma existir só para locais comerciais.

**contacts**: o array `contacts[]` dentro da mensagem tem `name` (`formatted_name`, `first_name`, `last_name`...), `org`, `phones[]` (`phone`, `wa_id`, `type`), `emails[]`, `addresses[]`, `urls[]` e `birthday`. Muitos campos são omitidos quando não compartilhados.

**interactive** (resposta a botão ou lista)
```json
{
  "context": { "from": "15550783881", "id": "wamid.HBgLMTY1MDM4Nzk0MzkVAgARGBI3MEM2RUJFNkI0RENGQTVDRjUA" },
  "from": "16505551234", "from_user_id": "US.13491208655302741918",
  "id": "wamid.HBgLMTY1MDM4Nzk0MzkVAgASGBQzQTZBQzg0MzQ4QjRCM0NGNkVGOAA=",
  "timestamp": "1750025136", "type": "interactive",
  "interactive": { "type": "button_reply", "button_reply": { "id": "cancel-button", "title": "Cancel" } }
}
```
Para lista: `"interactive":{"type":"list_reply","list_reply":{"id":"priority_express","title":"Priority Mail Express","description":"Next Day to 2 Days"}}`. O `context.id` é o wamid da mensagem que continha o botão ou a lista. O subtipo `nfm_reply` (Flows) não aparece nessa página (não confirmado na documentação).

**button** (resposta a botão de resposta rápida de template)
```json
{ "context": { "from":"15550783881","id":"wamid.HBg..." }, "type":"button", "button": { "payload":"Unsubscribe", "text":"Unsubscribe" } }
```

**reaction**
```json
"reaction": { "message_id": "wamid.HBgLMTQxMjU1NTA4MjkVAgASGBQzQUNCNjk5RDUwNUZGMUZEM0VBRAA=", "emoji": "👍" }
```
Se o usuário remove a reação, o objeto `reaction` traz só `message_id`, sem `emoji`. Tratar o emoji como string opaca.

**order** (pedido via catálogo do WhatsApp)
```json
"order": { "catalog_id":"194836987003835","text":"Love these!",
  "product_items":[{"product_retailer_id":"di9ozbzfi4","quantity":2,"item_price":30,"currency":"USD"}] }
```

**unsupported**
```json
{ "errors":[{"code":131051,"title":"Message type unknown","message":"Message type unknown","error_data":{"details":"Message type is currently not supported."}}],
  "type":"unsupported", "unsupported":{"type":"edit"} }
```
O código `131060` ("mensagem indisponível") ocorre quando um usuário escreve pela primeira vez para um número que veio do app WhatsApp Business.

Há páginas de referência para os tipos `group`, `edit` e `revoke`, que não foram lidas (não confirmado na documentação). Ignorar com segurança tipos desconhecidos.

### Context
- **Resposta a botão, lista ou template:** `context.from` (número comercial) e `context.id` (wamid da mensagem original).
- **Botão "Message business" de produto:** `context.referred_product = {catalog_id, product_retailer_id}`.
- **Encaminhada:** `context.forwarded: true` (até 5 encaminhamentos) ou `context.frequently_forwarded: true` (mais de 5).
- **Resposta citada a uma mensagem qualquer do chat:** a forma exata do `context` não está nas páginas lidas (não confirmado na documentação). Provavelmente é o mesmo `context.id`.

### Referral (anúncio Click to WhatsApp)
Presente em `messages[].referral` quando a mensagem vem de um anúncio:
```json
"referral": { "source_url":"https://fb.me/3cr4Wqqkv","source_id":"120226305854810726","source_type":"ad",
  "body":"Summer Succulents are here!","headline":"Chat with us","media_type":"image",
  "image_url":"https://scontent.xx.fbcdn.net/...","ctwa_clid":"Aff-n8ZT...","welcome_message":{"text":"Hi there!"} }
```

### Erros em webhooks
- `value.errors`: erros de sistema, app e conta.
- `value.messages[].errors`: erro de mensagem recebida (`type: unsupported`).
- `value.statuses[].errors`: falha de envio.

---

## 4. Statuses

**Fonte:** `.../webhooks/reference/messages/status.md`.

### Shape
```json
"statuses": [{
  "id": "wamid.HBgLMTY1MDM4Nzk0MzkVAgASGBQzQUFERjg0NDEzNDdFODU3MUMxMAA=",
  "status": "sent",
  "timestamp": "1750030073",
  "recipient_id": "16505551234",
  "recipient_user_id": "US.13491208655302741918",
  "conversation": { "id":"72b14d6b...","expiration_timestamp":"1750116480","origin":{"type":"marketing"} },
  "pricing": { "billable": true, "pricing_model": "PMP", "type": "regular", "category": "marketing" }
}]
```
- **`status`:** `sent`, `delivered`, `read`, `played` (áudio de voz) ou `failed`.
- **`id`:** é o wamid devolvido no envio. É assim que se casa o status com a mensagem enviada.
- **`recipient_id`:** é omitido se o envio foi para BSUID e o telefone não está disponível.
- **`biz_opaque_callback_data`:** devolvido se foi definido no envio. Serve para correlação.

### Ordem
- A página **não define ordem estrita** entre os webhooks.
- "Um status só é considerado read se foi delivered", mas se o usuário lê na hora **pode não vir um `delivered` separado**.
- Cada mensagem gera até três webhooks (sent, delivered, read).
- Para o código: tratar a ordem como não garantida, aplicar só transições "para frente" (sent < delivered < read) e aceitar pular estados.

### `conversation` e `pricing`
- **A partir da v24.0**, `conversation` some, exceto em conversas de free entry point.
- `pricing.pricing_model`: `PMP` (por mensagem) ou `CBP` (por conversa, só antes de 01/07/2025).
- `pricing.type`: `regular`, `free_customer_service`, `free_entry_point`, `free_group_customer_service`.
- `pricing.category`: `authentication`, `marketing`, `service`, `utility`, `group_*`, `referral_conversion`, `marketing_lite`. Mensagens de provedores de IA aparecem como `general_purpose_ai` (ver seção 6).
- O campo `billable` está **depreciado**: usar `type` + `category`.

### Failed
```json
"statuses": [{
  "id": "wamid.HBg...", "status": "failed", "timestamp": "1751142888", "recipient_id": "16505551234",
  "errors": [{ "code": 131049, "title": "This message was not delivered to maintain healthy ecosystem engagement.",
    "message": "<igual ao title>", "error_data": { "details": "..." },
    "href": "/documentation/business-messaging/whatsapp/support/error-codes" }]
}]
```

### Códigos de erro comuns
Fonte: `.../support/error-codes.md`.

| Código | Significado |
|---|---|
| 0, 190 | Token inválido ou expirado. |
| 3, 10, 200–299, 131005 | Permissão ausente ou removida. |
| 131000, 131016 | Erro desconhecido ou serviço indisponível. Fazer retry. |
| 131008, 131009 | Parâmetro ausente ou inválido. |
| 130429 | Throughput do número excedido. |
| 131056 | Muitas mensagens para o mesmo par remetente/destinatário em pouco tempo. |
| 131026 | Não entregável (destinatário sem WhatsApp, sem aceitar os termos ou com cliente desatualizado). |
| 131047 | **Mais de 24h desde a última mensagem do usuário.** Usar template. |
| 131048 | Envio restrito por spam ou bloqueios. |
| 131049 | Marketing retido para manter o engajamento saudável. Retry não garante entrega. |
| 131050 | Usuário optou por não receber marketing. Não reenviar. |
| 131051 | Tipo de mensagem não suportado. |
| 131052, 131053 | Falha ao baixar ou enviar mídia. |
| 131042 | Problema de pagamento ou cobrança. |
| 131031 | Conta restrita ou desabilitada. |
| 131062 | Tipo de mensagem não aceita destinatário BSUID (ex.: template de autenticação). Fonte: página de BSUID. |
| 132000, 132001, 132012 | Erros de parâmetros e idioma de template, ou template inexistente ou não aprovado. |
| 132015, 132016 | Template pausado por qualidade, ou desabilitado. |
| 130403 | Negócio bloqueou o usuário. |
| 133010 | Número não registrado. |

---

## 5. Envio

**Fontes:** `.../guides/send-messages` e as páginas `.../messages/*.md`.

### Requisição base
```
POST https://graph.facebook.com/v27.0/{phone_number_id}/messages
Content-Type: application/json
Authorization: Bearer <ACCESS_TOKEN>
```
Corpo comum:
```json
{ "messaging_product": "whatsapp", "recipient_type": "individual", "to": "+16505551234", "type": "<tipo>", "<tipo>": { } }
```
- **Enviar a BSUID:** trocar `to` por `"recipient": "US.13491208655302741918"`. Se `to` e `recipient` vierem juntos, vale `to`. Sem nenhum dos dois, erro 100.

### Resposta (o envio foi aceito, não entregue)
```json
{ "messaging_product":"whatsapp",
  "contacts":[{"input":"+16505551234","wa_id":"16505551234"}],
  "messages":[{"id":"wamid.HBgLMTY0NjcwNDM1OTUVAgARGBI1RjQyNUE3NEYxMzAzMzQ5MkEA"}] }
```
Para BSUID, `contacts` traz `input` e `user_id` (sem `wa_id`). Guardar `messages[0].id` para casar com os statuses.

### Texto (até 4096 caracteres)
```json
{ "messaging_product":"whatsapp","recipient_type":"individual","to":"+16505551234","type":"text",
  "text":{ "preview_url": true, "body":"Seu pedido foi enviado." } }
```
Responder citando uma mensagem (`context.message_id` no envio) não aparece na página lida (não confirmado na documentação).

### Template
```json
{ "messaging_product":"whatsapp","recipient_type":"individual","to":"+16505551234","type":"template",
  "template":{ "name":"order_confirmation","language":{"code":"en_US"},
    "components":[{"type":"body","parameters":[{"type":"text","text":"Jessica"},{"type":"text","text":"SKBUP2-4CPIG9"}]}] } }
```

### Interactive: botões de resposta
Limites: **no máximo 3 botões**, rótulo até 20 caracteres (e único), `id` até 256, corpo até 1024, rodapé até 60.
```json
{ "messaging_product":"whatsapp","recipient_type":"individual","to":"+16505551234","type":"interactive",
  "interactive":{ "type":"button",
    "body":{"text":"Confirmar o pedido?"},
    "footer":{"text":"Loja Exemplo"},
    "action":{"buttons":[
      {"type":"reply","reply":{"id":"confirmar","title":"Confirmar"}},
      {"type":"reply","reply":{"id":"cancelar","title":"Cancelar"}} ]}}}
```

### Interactive: lista
Limites: 1 a 10 seções e **10 linhas no total**. Texto do botão até 20 caracteres, `title` de linha até 24, descrição de linha até 72, título de seção até 24, corpo até 4096, `id` de linha até 200.
```json
{ "messaging_product":"whatsapp","recipient_type":"individual","to":"+16505551234","type":"interactive",
  "interactive":{ "type":"list",
    "body":{"text":"Escolha uma opção"},
    "action":{"button":"Ver opções","sections":[
      {"title":"Entrega","rows":[
        {"id":"expresso","title":"Expresso","description":"1 a 2 dias"},
        {"id":"normal","title":"Normal","description":"3 a 7 dias"} ]}]}}}
```

### Mídia (envio)
- **Upload:** `POST /{phone_number_id}/media` em `multipart/form-data` com `file=@arquivo;type=<mime>` e `messaging_product=whatsapp`. Devolve `{"id":"<MEDIA_ID>"}`.
- **Retenção:** o ID gerado pelo upload expira em 30 dias, e o ID recebido por webhook em **7 dias**.
- **Mensagem com mídia:** `{"type":"image","image":{"id":"<MEDIA_ID>","caption":"..."}}`. O uso de `link` "não é recomendado". O JSON completo de envio de mídia não foi extraído (não confirmado na documentação).

| Tipo | Formatos | Tamanho máximo |
|---|---|---|
| Áudio | AAC, AMR, MP3, M4A, OGG (Opus, mono) | 16 MB |
| Documento | TXT, XLS(X), DOC(X), PPT(X), PDF | 100 MB |
| Imagem | JPEG, PNG | 5 MB |
| Sticker | WebP estático / animado | 100 KB / 500 KB |
| Vídeo | 3GP, MP4 (H.264 + AAC) | 16 MB |

### Mídia recebida (download)
1. `GET https://graph.facebook.com/{versao}/{media-id}` com Bearer. Resposta:
```json
{ "messaging_product":"whatsapp","url":"<URL_TEMPORARIA>","mime_type":"image/jpeg","sha256":"...","file_size":"...","id":"..." }
```
2. `GET <url>` **com o mesmo header `Authorization: Bearer`**. Sem token, a requisição falha.
3. **A URL expira em 5 minutos.** Em caso de 404, pedir nova URL.
4. Excluir: `DELETE /{media-id}`.

### Reação
```json
{ "messaging_product":"whatsapp","recipient_type":"individual","to":"+16505551234","type":"reaction",
  "reaction":{"message_id":"wamid.HBg...","emoji":"😀"} }
```
Só é possível reagir a uma mensagem **recebida do usuário**, com menos de 30 dias. A reação gera só o status `sent`.

### Marcar como lida e indicador de digitando
```json
{ "messaging_product":"whatsapp", "status":"read", "message_id":"<WAMID_RECEBIDO>", "typing_indicator":{"type":"text"} }
```
- Resposta: `{"success": true}`. Vale até 30 dias após o recebimento e marca também as anteriores como lidas.
- Sem `typing_indicator`, só marca como lida.
- O indicador some quando você responde ou **após 25 segundos**. É útil enquanto o agente processa.

---

## 6. Regras

### Janela de atendimento de 24 h
- A janela abre quando o usuário envia mensagem ou liga, e cada nova mensagem do usuário a reinicia para 24 h.
- Com a janela aberta, o negócio pode enviar qualquer tipo de mensagem, inclusive texto livre.
- Fechada, só templates aprovados. Tentar texto livre gera o erro 131047.
- Só é permitido escrever a usuários que deram opt-in.
- **Free entry point (FEP):** abre quando o usuário chega por anúncio Click to WhatsApp. Dura até 7 dias, com todas as categorias gratuitas.

### Templates
- **Categorias:** `authentication`, `marketing`, `utility`. A categoria afeta o preço.
- **Aprovação:** revisão automática, em até 24 h. Pode enviar: `APPROVED`. Não pode enviar: em revisão, rejeitado, pausado, desabilitado. O status chega por `message_template_status_update`.
- **Criar por API:** `POST https://graph.facebook.com/v23.0/{WABA_ID}/message_templates` com `name`, `category`, `language`, `parameter_format` (`named` ou `positional`) e `components`.
- **Teto:** 250 templates por Messaging account em portfolio não verificado, até 6000 em verificado.
- **Qualidade:** `GREEN`, `YELLOW`, `RED`, `UNKNOWN`. `YELLOW` e `RED` ainda podem ser enviados, mas há risco de pausa.

### Preço
- **Desde 01/07/2025 a cobrança é por mensagem**, e só a mensagem entregue é cobrada. Mensagens do usuário para o negócio não são cobradas.
- O valor varia por categoria e pelo país do destinatário.
- `service` (resposta dentro da janela de 24 h, por humano ou IA): 1.000 mensagens gratuitas por mês por número. **Desde 01/10/2026, as mensagens `service` acima dessa franquia são cobradas.**
- **Valores em moeda não foram obtidos** (não confirmado na documentação). Consultar a tabela de tarifas vigente da Meta.

### Limites de mensagens
- É o número máximo de **usuários únicos** para os quais o negócio pode enviar mensagens **fora da janela de atendimento** em 24 h móveis.
- **Tiers:** 250 (padrão), 2.000, 10.000, 100.000 e ilimitado.
- O limite é calculado **por business portfolio**. Num SaaS multi-empresa, cada empresa cliente tem o próprio portfolio, então o limite é por cliente.
- Conversas iniciadas pelo cliente, dentro da janela, não contam.

### Throughput
- **80 mensagens por segundo** por número por padrão, com aumento automático até 1.000.
- Erro **130429** ao exceder.

### Quality rating
- `GET /{phone_number_id}?fields=quality_rating` (`GREEN`, etc.), com o webhook `phone_number_quality_update`. A regra completa de queda de tier não foi lida (não confirmado na documentação).

### Política da Meta sobre IA
- **Fato oficial** (`.../pricing/ai-providers.md`): os Termos de Serviço foram atualizados em **15/01/2026**. Pela nova regra, "AI Providers" só podem oferecer assistentes de IA de propósito geral onde a lei obriga a Meta a permitir. No Brasil (+55), as mensagens `service` de AI Providers são cobradas desde 11/03/2026.
- A distinção "assistente de propósito geral proibido, atendimento de negócio com IA permitido" **não está redigida nas páginas oficiais lidas**. Ela vem de fontes secundárias (TechCrunch, 9to5mac, respond.io). O texto contratual (WhatsApp Business Solution Terms) não foi lido (não confirmado na documentação oficial). Conferir antes do lançamento.
- **Implicação:** o prompt do agente restringe o assunto ao escopo da loja, como já prevê o `docs/contexto.md`.

---

## 7. Números de teste e sandbox

- Ao concluir o Get Started no App Dashboard, um número de teste é gerado e registrado automaticamente. No painel API Setup ficam o "Generate access token", a escolha do número remetente e o cadastro de destinatários.
- A ferramenta de teste de webhook do App Dashboard simula eventos, inclusive os de BSUID.
- Pelo Embedded Signup há números 555, até 2 por conta, com **5 mensagens por 24 h** até o display name ser aprovado.
- **Não confirmado na documentação:** limite de destinatários de teste (comumente citado como 5), expiração do número de teste.
- **Limite de números registrados:** 2 por portfolio novo, que sobe para 20 após a verificação do negócio.
- Para o desenvolvimento local, é preciso uma URL HTTPS pública para o webhook (um túnel, como o ngrok).
- O app precisa estar em modo Live para receber alguns webhooks.

---

## 8. Implicações para o nosso sistema

Esta seção é recomendação de arquitetura, não texto da documentação.

### Modelo de dados
**Por empresa:** `empresa_id`, `business_id` (portfolio), `waba_id` e uma referência ao token da empresa. O valor do token não fica no banco em texto puro.

**Por número:** `phone_number_id` (único global, é a chave de roteamento do webhook), `waba_id`, `display_phone_number`, status e `empresa_id` (FK). Opcionais: `quality_rating`, tier e throughput. O payload não traz `empresa_id`: a empresa sai do `phone_number_id`.

**Por cliente final:**
- `user_id` (BSUID) é a chave lógica. Como é escopado ao portfolio, a unicidade é `UNIQUE(empresa_id, user_id)`.
- `wa_id`/telefone é **nullable**: pode faltar e pode aparecer depois.
- Também: `profile.name` e `username` (nullable).
- Uma tabela de aliases (`previous_user_id`) liga identidades antigas quando chegam `user_changed_user_id` ou `user_changed_number`.
- Não correlacionar clientes entre empresas.

**Por mensagem:**
- `wamid` (`UNIQUE`, chave de idempotência), direção, `type`, `timestamp` e `context_wamid`.
- Mídia: `media_id`, `mime_type`, `sha256` e `caption`. Baixar e guardar a mídia no nosso storage, porque o id expira em 7 dias e a URL em 5 minutos.
- Status com o momento de cada transição, `error_code` e `pricing`.
- O payload bruto do webhook.

### Segredos
- App secret, verify token e tokens de acesso não vão para o banco em texto puro. O app secret e o verify token ficam em variável de ambiente ou num secrets manager. Os tokens por empresa ficam numa coluna criptografada (AES-GCM com chave mestra fora do banco) ou num cofre, com só a referência na tabela.
- Nunca logar token, app secret nem o header `Authorization`. Monitorar os erros 190 e 0 para prever renovação.

### Fluxo recomendado
1. **Receber** o POST e ler o **corpo bruto**.
2. **Validar** o `X-Hub-Signature-256` (HMAC-SHA256 com o app secret, comparação em tempo constante). Se falhar, 4xx.
3. **Persistir o payload bruto** e **responder 200 imediatamente**.
4. **Enfileirar** (fila, ou tabela `webhook_evento` com `SELECT ... FOR UPDATE SKIP LOCKED` no monolito).
5. **Processar de forma idempotente** cada item de `messages[]` e `statuses[]`, com `UNIQUE(wamid)` e status só para frente. Resolver a empresa e o número pelo `phone_number_id`.
6. Chamar `AtendimentoService.processarMensagem(...)` com a identidade do cliente (BSUID).
7. **Responder:** marcar como lida com `typing_indicator` enquanto o agente processa, enviar pela Cloud API e guardar o `messages[0].id`.
8. **Respeitar a janela de 24 h:** fora dela, só template. Tratar os erros 131047, 130429/131056 (com backoff) e 190 (token).

### Notas
- **Multi-tenant:** um app Meta único (Tech Provider), com um app secret e um verify token globais, um business token por empresa e um webhook único, roteado pelo `phone_number_id`.
- **Erros de envio:** retry com backoff em 130429, 131016, 131000 e 131056. Nunca reenviar em 131050.
- **Tipo `unsupported` (131051):** responder que não é possível ler esse tipo de mensagem, ou passar a conversa para um atendente.
- **Envio sem chave de idempotência:** a documentação lida não descreve uma (não confirmado na documentação). Reenviar depois de um timeout pode duplicar a mensagem.

---

## 9. URLs consultadas

Todas com prefixo `https://developers.facebook.com`.

Lidas:
- `/docs/whatsapp/cloud-api/webhooks/components`
- `/docs/whatsapp/cloud-api/webhooks/payload-examples`
- `/docs/whatsapp/cloud-api/guides/set-up-webhooks`
- `/docs/whatsapp/cloud-api/guides/send-messages`
- `/docs/whatsapp/cloud-api/reference/media`
- `/docs/whatsapp/pricing`
- `/documentation/business-messaging/whatsapp/llms.txt` (índice)
- `/documentation/business-messaging/whatsapp/webhooks/overview.md`
- `/documentation/business-messaging/whatsapp/webhooks/create-webhook-endpoint.md`
- `/documentation/business-messaging/whatsapp/webhooks/reference/messages.md`
- `/documentation/business-messaging/whatsapp/webhooks/reference/messages/status.md`
- `/documentation/business-messaging/whatsapp/webhooks/reference/messages/{text,image,audio,video,document,sticker,location,contacts,button,reaction,order,interactive,unsupported,system}.md`
- `/documentation/business-messaging/whatsapp/business-scoped-user-ids.md`
- `/documentation/business-messaging/whatsapp/pricing/ai-providers.md`
- `/documentation/business-messaging/whatsapp/messages/{interactive-reply-buttons-messages,interactive-list-messages,mark-message-as-read,reaction-messages,text-messages}.md`
- `/documentation/business-messaging/whatsapp/typing-indicators.md`
- `/documentation/business-messaging/whatsapp/business-phone-numbers/media.md`
- `/documentation/business-messaging/whatsapp/business-phone-numbers/phone-numbers.md`
- `/documentation/business-messaging/whatsapp/access-tokens.md`
- `/documentation/business-messaging/whatsapp/embedded-signup/{overview,implementation,onboarding-customers-as-a-tech-provider}.md`
- `/documentation/business-messaging/whatsapp/{throughput,messaging-limits}.md`
- `/documentation/business-messaging/whatsapp/templates/{overview,template-quality}.md`
- `/documentation/business-messaging/whatsapp/support/error-codes.md`
- `/documentation/business-messaging/whatsapp/{get-started,about-the-platform}.md`

Fontes secundárias (só para a política de IA): TechCrunch (18/10/2025), 9to5mac (20/10/2025), respond.io (blog `whatsapp-ai-chatbot-policy`).

Não lidas (por isso há itens "não confirmado"): `webhooks/override.md`; `webhooks/reference/messages/{group,edit,revoke,errors}.md`; `embedded-signup/{errors,versions}.md`; as páginas de envio de mídia e template; a documentação de mTLS; o WhatsApp Business Solution Terms.
