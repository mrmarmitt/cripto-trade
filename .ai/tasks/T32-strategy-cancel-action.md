# T32 — Ação `SHOULD_CANCEL` no Contrato da Estratégia

**Complexidade:** Alta  
**Responsável:** Claude  
**Dependências:** T33 (caminho outbound de cancelamento — `OrderDispatchPort.cancel`)  
**Status:** Pendente

---

## Descrição

Hoje a estratégia só emite três decisões: `SHOULD_BUY`, `SHOULD_SELL`, `SHOULD_HOLD` (`TradingAction`). Ela **enxerga** as ordens em trânsito — `StrategyContextDto.pendingOrders` carrega `List<PendingOrderDto>` com `transactionId`, `status`, `price`, `requestedAt` — mas **não pode agir** sobre elas.

Com ordens LIMIT, há uma decisão de negócio legítima que só a estratégia conhece: **puxar uma ordem aberta antes do fill porque a tese mudou** (ex.: cruzamento de média reverteu, sinal de saída antecipada, condição de risco). O gatilho aqui é **alpha**, não tempo — nada de TTL global de execução.

Esta task adiciona a ação de cancelamento ao contrato da estratégia, fechando o ciclo: a estratégia já recebe as ordens pendentes e passa a poder pedir o cancelamento de uma delas.

---

## Contexto técnico

### Contrato atual da estratégia

- `TradingStrategy.executeStrategy(input, context)` → `StrategyOutputDto`
- `StrategyOutputDto`: `decision` (`TradingAction`), `quantity`, `targetLotId`, `confidence`, `reasoning`, ...
- `TradingAction`: `SHOULD_BUY`, `SHOULD_SELL`, `SHOULD_HOLD`
- Roteamento: `ProcessTradeSignalUseCase.processRunner()` → `signalEvaluator.isHold()` curto-circuita HOLD; senão `processTradeSignal()` roteia para `BuySignalHandler`/`SellSignalHandler`.

### O que já está pronto (plumbing)

- A estratégia **já recebe** as ordens em trânsito via `StrategyContextDto.pendingOrders` (montado por `RunnerContextAssembler`). `PendingOrderDto.transactionId` identifica unicamente a ordem a cancelar.
- O caminho outbound de cancelamento (`OrderDispatchPort.cancel`) é entregue pela **T33**.

Ou seja, a estratégia tem a informação de entrada e o sistema terá o canal de saída. Falta o **contrato de saída** (ação) e o **handler** que o materializa.

---

## Solução proposta

### 1. Estender o contrato de decisão

Adicionar à `TradingAction`:

```java
public enum TradingAction {
    SHOULD_BUY,
    SHOULD_SELL,
    SHOULD_HOLD,
    SHOULD_CANCEL   // novo
}
```

Adicionar ao `StrategyOutputDto` o alvo do cancelamento e factory dedicada:

```java
UUID targetTransactionId;   // ordem em trânsito a cancelar (de PendingOrderDto)

public static StrategyOutputDto cancel(String strategyName, UUID targetTransactionId, String reasoning) {
    Objects.requireNonNull(targetTransactionId, "targetTransactionId required for cancel");
    return new StrategyOutputDto(strategyName, TradingAction.SHOULD_CANCEL,
        BigDecimal.ZERO, BigDecimal.ZERO, null, targetTransactionId, reasoning, Instant.now(), Map.of());
}
```

Ajustar `shouldTrade()` se necessário (cancel não é trade de abertura/fechamento — provavelmente fica fora de `shouldTrade()` e ganha `shouldCancel()`).

### 2. Handler de cancelamento

Criar `CancelSignalHandler` (pacote `usecase/runner/processsignal`), no mesmo estilo de `BuySignalHandler`/`SellSignalHandler`:

- Valida que `targetTransactionId` pertence ao runner e está em estado cancelável (`PENDING`/`SUBMITTED`/`PARTIAL`).
- Rejeita silenciosamente (log) se a transação já é terminal ou não pertence ao runner — a estratégia pode estar operando com contexto levemente defasado.
- Emite `orderDispatch.cancel(OrderCancelCommand)` (porta da T33).
- **Não** marca terminal localmente; o `CANCELED` real chega via stream e é conciliado (mesma garantia idempotente do restante do sistema).

### 3. Roteamento em `ProcessTradeSignalUseCase`

Em `processRunner()`/`processTradeSignal()`, antes do roteamento BUY/SELL atual, tratar `SHOULD_CANCEL`:

```java
if (strategyOutput.decision() == TradingAction.SHOULD_CANCEL) {
    cancelSignalHandler.handle(runner, strategyOutput);
    return;
}
```

`SHOULD_CANCEL` não passa por normalização de quantidade nem por `CapitalReservationPolicy` (não reserva capital). Não exige `requiresOpenPositionCheck`.

### 4. Estratégia de referência

Atualizar/estender a estratégia de exemplo (ou criar variante) para exercitar o caminho: se há ordem de compra pendente e o sinal inverteu para venda antes do fill, emitir `SHOULD_CANCEL` da pendente em vez de empilhar nova ordem. Apenas como cobertura de teste/demonstração — não alterar a semântica da `SimpleMovingAverageStrategy` em produção sem decisão explícita.

---

## Arquivos a modificar / criar

| Arquivo | Mudança |
|---|---|
| `core/.../enums/TradingAction.java` | Adicionar `SHOULD_CANCEL` |
| `core/.../dto/strategy/StrategyOutputDto.java` | Campo `targetTransactionId` + factory `cancel(...)` + `shouldCancel()` |
| `core/.../usecase/runner/processsignal/CancelSignalHandler.java` | Novo — valida alvo e despacha cancel (via verbo da T33) |
| `core/.../usecase/runner/processsignal/ProcessTradeSignalUseCase.java` | Roteamento de `SHOULD_CANCEL` |
| `core/.../usecase/runner/processsignal/RunnerSignalPolicy.java` | Guardas para cancel (estado cancelável, ownership) |
| `strategy/.../impl/...` | Estratégia/variante de referência que emite `SHOULD_CANCEL` (cobertura) |
| Testes em `core/.../processsignal/` | Cobrir roteamento, ownership, estado não-cancelável, idempotência |

---

## Riscos / pontos de atenção

- **Fronteira arquitetural:** `SHOULD_CANCEL` deve permanecer uma **decisão**; toda a tradução para a exchange fica no adapter via `OrderDispatchPort.cancel`. Nenhum detalhe de provider entra em `strategy/` ou no contrato da estratégia.
- **Contexto defasado:** a estratégia decide sobre `pendingOrders` montado no início do tick; a ordem pode ter enchido nesse meio-tempo. O `CancelSignalHandler` deve validar o estado atual e tratar "não mais cancelável" como no-op logado, não como erro.
- **Corrida cancel × fill:** mesma da T31 — sem marcação terminal otimista; conciliação idempotente decide.
- **Fronteira com T31/T33:** a **T33** entrega o verbo `OrderDispatchPort.cancel` (só o mecanismo, sem gatilho). A **T31** cobre **reserva órfã/zombie** (`PENDING` sem ordem na exchange) via query + conciliação — não cancela ordem viva e nem usa o verbo. Cancelar ordem **viva** é responsabilidade desta task (decisão de alpha). Se um dia houver TTL de execução para ordem viva, deve ser opt-in por runner (default desligado) — nunca um default global que anule a estratégia.
- **Compatibilidade do enum:** adicionar valor a `TradingAction` exige revisar todo `switch`/`if` sobre a enum (ex.: `StrategySignalEvaluator`, mapeadores) para tratar o novo caso explicitamente.

---

## Critérios de aceitação

1. Uma estratégia consegue emitir `StrategyOutputDto` com `SHOULD_CANCEL` e `targetTransactionId`.
2. `ProcessTradeSignalUseCase` roteia `SHOULD_CANCEL` para `CancelSignalHandler` sem passar por reserva de capital ou normalização de quantidade.
3. Cancelamento de transação que não pertence ao runner ou já é terminal é tratado como no-op logado (sem exceção que derrube o tick).
4. Cancel válido despacha `OrderDispatchPort.cancel`; o `CANCELED` chega via stream e é conciliado pelo caminho idempotente, liberando capital quando aplicável.
5. Nenhum detalhe de exchange vaza para `strategy/` nem para o contrato `StrategyOutputDto`.
6. Todos os `switch`/`if` sobre `TradingAction` tratam `SHOULD_CANCEL` explicitamente (sem fall-through silencioso).
