# TD1 — Telemetria do canal USER_DATA

**Tipo:** Débito técnico  
**Origem:** T4 — User Data Stream (PR #89)  
**Complexidade:** Baixa  
**Impacto:** Observabilidade (não bloqueia funcionalidade)

---

## Problema

O `ProcessUserMessageHandler` não chama `manager.onMessageReceived()` ao contrário do `ProcessMessageHandler` que faz isso para o canal MARKET. Com a introdução do `ConnectionKey` por canal (T4), o endpoint `/websocket/stats/all` expõe stats separadas por canal — mas as contagens de mensagens e erros do canal `USER_DATA` ficam zeradas mesmo com tráfego real de `executionReport`.

## Impacto atual

- Stats de `USER_DATA` (mensagens recebidas, erros) sempre em zero
- Dificuldade de observar saúde do user data stream em produção
- Não afeta corretude funcional — fills, conciliação e reconnect continuam operando normalmente

## Solução esperada

`ProcessUserMessageHandler` deve resolver o `ConnectionKey.userStream(exchangeName)` a partir do `MessageContext` e chamar `manager.onMessageReceived()` (ou `manager.onMessageError()` em caso de falha), espelhando o comportamento do `ProcessMessageHandler`.

Requer que `MessageContext` carregue o `StreamChannel` ou que o handler consiga derivá-lo a partir do contexto da mensagem.

## Critério de pronto

`GET /websocket/stats/all` retorna contagens corretas para o canal `USER_DATA` após receber eventos `executionReport`.
