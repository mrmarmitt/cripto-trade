# T31 — TTL / Cancelamento de Ordem Limite Aberta em Runtime

**Complexidade:** Alta  
**Responsável:** Claude  
**Dependências:** Nenhuma (estabelece o caminho outbound de cancelamento reusado por T32)  
**Status:** Pendente

---

## Descrição

O caminho real de execução envia **ordens LIMIT** (`OrderDispatchAdapter` materializa `OrderType.LIMIT`). Uma ordem limite pode ficar aberta na exchange (`SUBMITTED`/`NEW`) indefinidamente quando o preço não chega nela. Hoje nada cancela essa ordem por iniciativa do sistema em runtime:

- O **watchdog** (`RecoverStaleTransactionsUseCase`, eligible `SUBMITTED`/`PARTIAL`) apenas **reconcilia** o status real com a exchange. Se a exchange responde `NEW`, a transação continua viva — o watchdog não a encerra, porque ela não está em limbo técnico, está legitimamente aberta.
- O **`PortfolioReservationTtlUseCase`** expira reservas de capital, mas só cobre `PENDING` **sem `exchangeOrderId`** e **só roda no boot** (phase2).

Resultado: uma ordem de compra limite que não enche mantém **capital reservado preso** até encher, até reinício da aplicação, ou até cancelamento **manual** via API. Falta uma política de execução que, em runtime, encerre ordens limite velhas demais — liberando capital e (opcionalmente) reprecificando.

> Esta task é **política de execução** (order management), não alpha de estratégia. A decisão "esta ordem está velha demais" é guiada por idade/tempo, não pela tese de mercado. A decisão de negócio "mudei de ideia" fica na T32.

---

## Contexto técnico

### Capacidade de cancelamento já existente

- `ExchangeOrderExecutionPort.cancelOrder(SendCancelOrderRequest)` — documentado como *"cancelamento por watchdog/recovery"*. Implementado nos adapters.
- `CancelOrderProcessor` (Binance/Coinbase) monta a mensagem `order.cancel`.
- `OrderManagementService.unsubscribe()` já usa esse caminho para cancelamento **manual** via WebSocket.

Ou seja, o transporte de cancelamento existe. Falta o **disparo automático por política de runtime** e a integração com conciliação/liberação de capital.

### Liberação de capital ao cancelar

- A conciliação de um `CANCELED`/`EXPIRED` já dispara liberação de margem (`MarginReleaseBuilder`, `PortfolioStrategyRunnerOrderUpdateListener`). O cancelamento por TTL deve passar pelo **mesmo** `ConciliationOrderUpdateExecutor`, garantindo que a liberação de capital reuse o caminho idempotente existente (`ConciliationOrderUpdateIdempotencyTest`).

### Estados elegíveis

| Estado | Hoje | Com T31 |
|---|---|---|
| `PENDING` sem `exchangeOrderId` | TTL só no boot | TTL também em runtime (reusa lógica de `PortfolioReservationTtlUseCase`) |
| `SUBMITTED`/`NEW` aberto na exchange | nunca encerrado em runtime | cancela na exchange após idade > TTL |
| `PARTIAL` | watchdog reconcilia | cancelar **apenas o remanescente** após TTL (decisão explícita — ver risco) |

---

## Solução proposta

### 1. Porta outbound de cancelamento no core

Hoje `OrderDispatchPort` só tem `dispatch` (submit fire-and-forget). Adicionar a capacidade de cancelamento como contrato de domínio, espelhando o estilo fire-and-forget:

```java
public interface OrderDispatchPort {
    void dispatch(OrderDispatchCommand command);
    void cancel(OrderCancelCommand command);   // novo
}
```

`OrderCancelCommand`: record no core (`dto/runner/`) com `clientOrderId`, `runnerId`, `symbol`, `exchangeId`. O adapter Spring traduz para `SendCancelOrderRequest` (mesmo padrão de `OrderDispatchAdapter` → `OrderDispatchCommand`). Nenhum DTO de exchange entra no core.

### 2. Use case de TTL de ordem em runtime

Criar `RuntimeOrderTtlUseCase` no core (pacote `usecase/runner`), no mesmo espírito do `RecoverStaleTransactionsUseCase`:

- Busca transações em estados elegíveis com `requestedAt`/`updatedAt` anterior ao cutoff (`findByStatusesUpdatedBefore`, já existe).
- Para `PENDING` sem `exchangeOrderId`: aplica expiração sintética **local** (reusa `buildSyntheticExpired` de `PortfolioReservationTtlUseCase` — considerar extrair para um helper compartilhado para evitar duplicação).
- Para `SUBMITTED`/`PARTIAL` com `exchangeOrderId`: emite `orderDispatch.cancel(...)`. O `CANCELED` real chega de forma assíncrona via User Data Stream e é conciliado pelo caminho normal — **não** marcar terminal localmente de forma otimista (evita divergência se o cancel for rejeitado por fill simultâneo).
- Idempotência: não reenviar cancel para uma transação que já tem cancel em trânsito (marcar `cancelRequestedAt` na `Transaction` ou checar janela mínima entre tentativas).

### 3. Scheduler no Spring

Criar `RuntimeOrderTtlWatchdog` espelhando `RunnerTransactionRecoveryWatchdog`:

```java
@Scheduled(
    fixedDelayString = "${runner.order-ttl.interval-ms:60000}",
    initialDelayString = "${runner.order-ttl.interval-ms:60000}")
public void scheduledTick() { ... }
```

Propriedades em `RuntimeOrderTtlProperties` (espelhar `RunnerTransactionRecoveryProperties`): `enabled`, `ttlMs`, `maxPerRun`.

### 4. (Opcional, fase 2 da task) Cancel-replace / reprice

Após cancelar por TTL, opcionalmente reemitir a ordem ao preço de mercado corrente. **Recomendação: deixar fora do MVP** — cancelar e liberar capital já resolve o risco principal. Reprice automático reintroduz a ordem e pode criar loop de cancel/replace; tratar como evolução separada se houver demanda.

---

## Configuração (application.yml)

```yaml
runner:
  order-ttl:
    enabled: true
    interval-ms: 60000        # frequência do watchdog
    ttl-ms: 900000            # 15 min — idade máxima de ordem limite aberta
    max-per-run: 50
```

---

## Arquivos a modificar / criar

| Arquivo | Mudança |
|---|---|
| `core/.../ports/outbound/exchange/OrderDispatchPort.java` | Adicionar `cancel(OrderCancelCommand)` |
| `core/.../dto/runner/OrderCancelCommand.java` | Novo — comando de cancelamento agnóstico |
| `core/.../usecase/runner/RuntimeOrderTtlUseCase.java` | Novo — política de TTL em runtime |
| `core/.../usecase/runner/...` (helper de expiração sintética) | Extrair `buildSyntheticExpired` compartilhado entre boot TTL e runtime TTL |
| `core/.../domain/runner/Transaction.java` | Possível campo `cancelRequestedAt` para idempotência de cancel |
| `spring-application/.../infrastructure/exchange/OrderDispatchAdapter.java` | Implementar `cancel(...)` → `SendCancelOrderRequest` |
| `spring-application/.../bootstrap/RuntimeOrderTtlWatchdog.java` | Novo — scheduler |
| `spring-application/.../bootstrap/RuntimeOrderTtlProperties.java` | Novo — propriedades |
| `spring-application/src/main/resources/application.yml` | Configuração do TTL |
| `spring-application/.../config/core/RunnerConfig.java` | Wiring do novo use case |

---

## Riscos / pontos de atenção

- **Corrida cancel × fill:** o cancel pode chegar à exchange no exato momento em que a ordem enche. Não marcar `CANCELED` localmente de forma otimista; deixar a conciliação idempotente decidir com o evento real (`ConciliationOrderUpdate`). O adapter deve tolerar resposta "ordem já não cancelável".
- **`PARTIAL`:** cancelar uma ordem parcialmente executada encerra apenas o remanescente. Garantir que a parte já executada permaneça contabilizada (lote/posição) e que só o saldo não preenchido libere reserva.
- **Liberação de capital:** deve fluir exclusivamente pelo `ConciliationOrderUpdateExecutor`, nunca por liberação direta — caso contrário diverge do invariante de conciliação.
- **Boot vs runtime:** garantir que o TTL de boot (`PortfolioReservationTtlUseCase`) e o de runtime não conflitem; idealmente compartilham a mesma lógica de expiração sintética.

---

## Critérios de aceitação

1. Uma ordem `PENDING` sem `exchangeOrderId` mais velha que `ttl-ms` é expirada em runtime (não só no boot), liberando o capital reservado.
2. Uma ordem `SUBMITTED`/`NEW` aberta na exchange mais velha que `ttl-ms` recebe `cancel` despachado para a exchange.
3. O `CANCELED` resultante chega via stream e é conciliado pelo caminho idempotente existente, liberando a margem via `MarginReleaseBuilder`.
4. Cancel não é reenviado em ciclos consecutivos para a mesma transação (idempotência).
5. Ordem `PARTIAL` cancelada preserva a quantidade já executada e libera apenas o remanescente.
6. `runner.order-ttl.enabled: false` desabilita o watchdog sem afetar boot TTL nem o recovery watchdog.
7. Nenhuma regra de exchange ou DTO de provider vaza para o `core` (porta `cancel` recebe `OrderCancelCommand` agnóstico).
