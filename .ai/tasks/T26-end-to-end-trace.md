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
| `core/.../usecase/runner/orderconciliation/ConciliationOrderUpdate.java` | **Único** site de `MDC.put/remove("transactionId")`: ancora o id durante o roteamento da conciliação e loga a própria falha in-scope |
| `core/.../usecase/runner/processsignal/ProcessTradeSignalUseCase.java` | Log de criação `signal: transaction created transactionId={} correlationId={} ...` (apenas no outcome DISPATCHED); `correlationId` lido do MDC do tick |
| `spring-application/src/main/resources/logback-spring.xml` | Adicionar `%cMDC{transactionId,tx}` no pattern do console |

---

## Critérios de aceitação

1. Query `{app="ctrade"} | json | transactionId="X"` retorna os logs **da conciliação** da transação X (PENDING->SUBMITTED, fills, terminal, falha de conciliação) — onde o `transactionId` está no MDC.
2. Demais fronteiras (criação do sinal, capital, recovery watchdog/boot) carregam `transactionId` **no texto da mensagem**, recuperáveis por filtro de linha `{app="ctrade"} |= "transactionId=X"`.
3. Log de criação de transação BUY/SELL contém `correlationId` do tick que originou o sinal (o "join" tick→transação).
4. `transactionId` aparece no formato de log do console (via `ConditionalMDCConverter`) apenas quando presente (i.e., durante a conciliação).
5. Nenhum comportamento funcional alterado — apenas adição de contexto de rastreamento.

---

## Notas de implementação (entregue — PR #131)

Divergências e decisões confirmadas no momento da entrega:

- **MDC `transactionId` centralizado na conciliação (decisão de design):** após iterações no
  review, o `MDC.put/remove("transactionId")` ficou **só** em `ConciliationOrderUpdate` — a
  classe que de fato *é* a conciliação. Os orquestradores que apenas *executam* uma conciliação
  (watchdog `RecoverStaleTransactionsUseCase`, boot `RunnerBootRecoveryUseCase`, engine
  `RecoverTransactionStatusUseCase`) e os fluxos de origem (`ProcessTradeSignalUseCase`,
  `CapitalEventListener`) **não** gerenciam MDC. Motivo: gerenciar MDC espalhado por várias
  classes (escopos cruzando métodos, save/restore para reentrância) tem alto risco de bug; o
  ganho de rastreabilidade estruturada do "originador" é facilmente coberto por `transactionId`
  no **texto** da mensagem. Resultado: um único site de MDC, simples (put/remove local).
- **Consequência de tracing:** `| json | transactionId="X"` cobre a **conciliação** (estruturado);
  criação de sinal, capital e recovery são recuperados por `|= "transactionId=X"` (filtro de linha),
  pois carregam o id no texto. O 1º uso de MDC no core foi via `org.slf4j.MDC` direto (slf4j-api já
  é dependência de todo `@Slf4j`); descartada uma porta `TraceContextPort` (envolveria o MDC 1:1).
- **Log de falha in-scope na conciliação:** a `ConciliationOrderUpdate` loga a própria falha dentro
  do escopo do MDC antes de propagar — os chamadores externos (`ProcessMessageHandler`) só conhecem
  o `clientOrderId`, então sem esse log a falha de conciliação escaparia da query por `transactionId`.
- **Log de criação no `ProcessTradeSignalUseCase`:** `signal: transaction created transactionId={}
  correlationId={} ...` é emitido só no **outcome `DISPATCHED`** (transação persistida e despachada)
  — sinais descartados antes do commit não emitem, para não anunciar transação fantasma. O
  `correlationId` vem do MDC do **tick** (não do `transactionId`), então o join tick→transação
  independe do MDC de transactionId.
- **Acessores reais dos eventos:** a spec usa `event.transactionId()`; os eventos expõem o id
  via `confirmation().transactionId()` (`ExecutionConfirmedEvent`) e `release().transactionId()`
  (`MarginReleaseEvent`).
- **Testes:** o test runtime do `:core` ganhou binding `logback-classic` (sem binding o
  `MDCAdapter` do slf4j é NOP e o MDC não funciona em teste) + `logback-test.xml` silencioso.
