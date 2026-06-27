# T26 — Rastreamento End-to-End de um Sinal

**Complexidade:** Alta  
**Responsável:** Claude  
**Dependências:** T17 (Loki), T23 (melhoria de logs)  
**Status:** Concluído (PR #131)

---

## Descrição

Hoje o `correlationId` cobre a entrada da mensagem WebSocket até o processamento pelo handler, mas não atravessa as fronteiras assíncronas subsequentes — o evento de capital, o watchdog e o boot recovery operam com seus próprios IDs sem vínculo explícito ao tick original. Isso impossibilita reconstruir a história completa de um trade a partir de uma única query no Loki.

O objetivo é propagar um `traceId` que vincule todos os logs do ciclo de vida de uma operação:

```
tick recebido → sinal avaliado → ordem colocada → fill recebido → capital confirmado
```

Permitindo, a partir de um único ID, encontrar todos os logs de uma operação no Loki.

---

## Estado atual do rastreamento

| Fronteira | ID disponível | Propagado além? |
|---|---|---|
| WebSocket message → handler | `correlationId` (MDC) | Não — limpo ao sair do handler |
| Handler → `ProcessTradeSignalUseCase` | `correlationId` via MDC | Não propagado para eventos downstream |
| `ExecutionConfirmedEvent` | `matchId` + `transactionId` | Não vinculado ao correlationId original |
| `MarginReleaseEvent` | `transactionId` | Não vinculado ao correlationId original |
| Watchdog recovery | `transactionId` | Sem vínculo com tick original |
| DLQ entry | `clientOrderId` + `exchangeOrderId` | Sem vínculo com tick original |

O `transactionId` é o elo existente mais próximo — gerado no `BuySignalHandler` e presente em todos os eventos subsequentes. O `clientOrderId` derivado do `transactionId` também atravessa a exchange.

---

## Solução proposta

### Princípio de design

Não criar um sistema de tracing distribuído completo (OpenTelemetry seria overkill para este contexto). Em vez disso, propagar o `transactionId` como campo MDC em todas as fronteiras assíncronas onde uma transação está envolvida. O `transactionId` já existe e é o identificador canônico do ciclo de vida de uma operação.

### 1. Adicionar `transactionId` ao MDC nos handlers de capital

**`CapitalEventListener`** — ao consumir `ExecutionConfirmedEvent` e `MarginReleaseEvent`:

```java
MDC.put("transactionId", event.transactionId().toString());
try {
    // processamento
} finally {
    MDC.remove("transactionId");
}
```

Isso faz com que todos os logs dentro do processamento do evento (inclusive retries e recover) incluam o `transactionId` como campo estruturado no Loki.

### 2. Adicionar `transactionId` ao MDC no watchdog

**`RecoverStaleTransactionsUseCase`** — ao processar cada transação:

```java
MDC.put("transactionId", transaction.getId().toString());
try {
    recoverTransactionStatusUseCase.execute(request);
} finally {
    MDC.remove("transactionId");
}
```

### 3. Propagar `correlationId` original para o log de criação da transação

**`BuySignalHandler` / `SellSignalHandler`** — ao persistir a transação, incluir o `correlationId` atual (do MDC) no log de criação:

```java
log.info("signal: transaction created transactionId={} correlationId={} runnerId={} side=BUY",
        transaction.getId(), MDC.get("correlationId"), runnerId);
```

Isso cria o elo entre o `correlationId` do tick e o `transactionId` da operação — o "join" para reconstruir a história completa.

### 4. Adicionar `transactionId` ao log de conciliação

**`ConciliationOrderUpdate`** — nos logs existentes de `PENDING->SUBMITTED`, `BUY fill`, `SELL match persisted`, já há `transactionId`. Verificar que o MDC também o contém para que todos os campos adicionais (correlationId, exchangeName) apareçam automaticamente.

### 5. Propagar para DLQ entry

**`CapitalEventListener.@Recover`** — ao persistir DLQ entry, o `transactionId` já está no MDC. Verificar que o log `CAPITAL_DLQ_PERSISTED` emitido inclui `transactionId`.

### 6. Atualizar `ConditionalMDCConverter` no logback

Adicionar `transactionId` como campo MDC renderizado no padrão de log:

```xml
%cMDC{correlationId,correlation}%cMDC{transactionId,tx}%cMDC{exchangeName,exchange}%cMDC{connectionId,conn}
```

---

## Query resultante no Loki

Com a propagação implementada, reconstruir a história de um trade:

```logql
{app="ctrade"} | json | transactionId="uuid-aqui"
```

Retorna — em ordem cronológica:
1. `signal: transaction created transactionId=X correlationId=Y` — origem no tick
2. `capital reserved transactionId=X` — reserva de capital
3. `PENDING->SUBMITTED transactionId=X exchangeOrderId=Z` — ordem aceita
4. `BUY fill positionId=P transactionId=X` — fill recebido
5. `executionConfirmedReaction: matchId=M transactionId=X` — capital confirmado
6. (ou) `marginReleasedReaction: transactionId=X` — margem liberada

E via `correlationId=Y`:
```logql
{app="ctrade"} | json | correlationId="uuid-do-tick"
```
Retorna o tick original que originou o sinal.

---

## O que esta task NÃO faz

- Não implementa OpenTelemetry ou tracing distribuído com spans/traces
- Não propaga traceId para a exchange ou sistemas externos
- Não altera o `correlationId` existente — apenas adiciona `transactionId` ao MDC nas fronteiras onde faltava

---

## Arquivos a modificar

| Arquivo | Mudança |
|---|---|
| `spring-application/.../handler/CapitalEventListener.java` | MDC `transactionId` no consumo de `ExecutionConfirmedEvent` e `MarginReleaseEvent` |
| `core/.../usecase/runner/RecoverStaleTransactionsUseCase.java` | MDC `transactionId` por transação no loop do watchdog |
| `core/.../usecase/runner/processsignal/BuySignalHandler.java` | Log de criação inclui `correlationId` do MDC |
| `core/.../usecase/runner/processsignal/SellSignalHandler.java` | Log de criação inclui `correlationId` do MDC |
| `spring-application/.../config/logback/ConditionalMDCConverter.java` | Adicionar `transactionId` ao padrão de log |
| `spring-application/src/main/resources/logback-spring.xml` | Adicionar `%cMDC{transactionId,tx}` no pattern |

---

## Critérios de aceitação

1. Query `{app="ctrade"} | json | transactionId="X"` retorna logs de todas as fronteiras do ciclo de vida da transação X.
2. Log de criação de transação BUY/SELL contém `correlationId` do tick que originou o sinal.
3. Logs de `CapitalEventListener` (incluindo retries e recover) contêm `transactionId` no MDC.
4. Logs do watchdog (`RecoverStaleTransactionsUseCase`) contêm `transactionId` por transação processada.
5. `transactionId` aparece no formato de log do console (via `ConditionalMDCConverter`) apenas quando presente.
6. Nenhum comportamento funcional alterado — apenas adição de contexto de rastreamento.

---

## Notas de implementação (entregue — PR #131)

Divergências e decisões confirmadas no momento da entrega:

- **MDC no core (decisão arquitetural):** este é o 1º uso de MDC no core (antes só no
  `spring-application`). Optou-se por `org.slf4j.MDC` direto: `slf4j-api` já é dependência do
  core (todo `@Slf4j`), e trace context é a mesma categoria de observabilidade que o core já
  loga. Uma porta `TraceContextPort` só envolveria o MDC thread-local 1:1 — cerimônia sem
  desacoplamento real.
- **Log de criação centralizado:** o log `signal: transaction created ... correlationId=...`
  ficou no `ProcessTradeSignalUseCase` (não em `Buy/SellSignalHandler`, como sugeria a spec).
  É o ponto único que conhece o `side` e ancora o `transactionId` no MDC para as linhas de
  capital/dispatch. O log de criação só é emitido no **outcome `DISPATCHED`** (transação
  persistida e ordem despachada) — sinais descartados antes do commit (BUY recusado por capital,
  SELL sem posição, falha de lock que faz rollback do `TransactionTemplate`) **não** emitem o log,
  para a query do Loki não retornar transações fantasma (ajuste de review do PR #131). O escopo do
  MDC, porém, cobre todo o handler, então os logs de rejeição permanecem rastreáveis pelo id.
- **Acessores reais dos eventos:** a spec usa `event.transactionId()`; os eventos expõem o id
  via `confirmation().transactionId()` (`ExecutionConfirmedEvent`) e `release().transactionId()`
  (`MarginReleaseEvent`).
- **Save/restore do MDC:** onde a conciliação pode rodar aninhada (watchdog→recover), o
  `transactionId` é salvo e restaurado em vez de simplesmente removido, evitando perder o
  vínculo do escopo externo.
- **Item 5 (DLQ):** `CAPITAL_DLQ_PERSISTED` passou a carregar `transactionId` automaticamente —
  o caminho `@Recover` agora seta o MDC, então o id vira campo JSON no Loki sem mudar a mensagem.
- **Testes:** o test runtime do `:core` ganhou binding `logback-classic` (sem binding o
  `MDCAdapter` do slf4j é NOP e o MDC não funciona em teste) + `logback-test.xml` silencioso.
