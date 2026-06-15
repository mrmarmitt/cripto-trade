# T20 — Preencher `executed_at` com o timestamp da exchange durante boot recovery

**Complexidade:** Baixa  
**Responsável:** Codex  
**Dependências:** T1 (transaction recovery), T18 (trade reconciliation report)  
**Status:** Concluída

---

## Motivação

Durante homologação na testnet Binance, o endpoint de reconciliação retornou uma entrada `EXCHANGE_ONLY` para uma ordem que existia no banco como `FILLED`. A investigação revelou que:

1. A ordem foi executada na exchange em `2026-06-12T17:58:01Z`.
2. O app foi encerrado abruptamente antes de processar o evento de fill via WebSocket.
3. No boot seguinte, o `RunnerBootRecoveryUseCase` (Step 4) consultou a exchange, obteve o status `FILLED` e aplicou o update via `ConciliationOrderUpdateExecutor`.
4. A Transaction foi persistida com `status = FILLED`, mas `executed_at = null`.
5. A query `findFilledBySymbolAndPeriod` usa `COALESCE(t.executed_at, t.updated_at)` para filtrar o período. Com `executed_at` nulo, o fallback recaiu sobre `updated_at`, que reflete **quando o boot recovery rodou** (2026-06-15), não quando a ordem foi executada (2026-06-12).
6. A consulta de reconciliação com janela `from=2026-06-09 to=2026-06-14` excluiu a Transaction por estar fora do range, gerando falso `EXCHANGE_ONLY`.

O mesmo problema ocorre em qualquer cenário onde a conciliação é aplicada tardiamente (boot recovery, reconciliação manual futura), desde que `executed_at` não seja preenchido com o timestamp real da exchange.

---

## Causa raiz

`BinanceOrderMapper.fromNode()` (`adapter-binance/.../rest/BinanceOrderMapper.java`) tenta ler o timestamp nesta ordem:

```java
long timeMs = node.path("time").asLong(0);
if (timeMs == 0) {
    timeMs = node.path("transactTime").asLong(0);
}
timeMs > 0 ? Instant.ofEpochMilli(timeMs) : Instant.now()
```

O campo `transactTime` está presente apenas nas respostas de *order placement*, **não** na resposta de `GET /api/v3/order`. O campo `updateTime` — que para ordens `FILLED` contém o timestamp exato do fill — nunca é lido. Quando `time` vem 0 ou ausente na testnet, o mapper cai em `Instant.now()`, que corresponde ao momento do boot recovery, não ao momento do fill real.

O `OrderDataDto.timestamp()` resultante com `Instant.now()` é então passado por `normalizeQueriedOrder` em `RecoverTransactionStatusUseCase` e gravado como `executed_at` na `Transaction`.

---

## Solução esperada

1. Atualizar `BinanceOrderMapper.fromNode()` para incluir `updateTime` na cadeia de fallback, com prioridade sobre `time`:
   ```
   updateTime → transactTime → time → Instant.now()
   ```
   Para ordens `FILLED`, `updateTime` é o timestamp do fill (campo `updateTime` na resposta de `GET /api/v3/order`).
2. Adicionar teste unitário em `BinanceOrderMapperTest` cobrindo resposta de `GET /api/v3/order` com `updateTime` preenchido e `transactTime` ausente, verificando que o `OrderDataDto.timestamp()` reflete `updateTime`.
3. Adicionar teste de integração ou contrato cobrindo o cenário end-to-end: boot recovery de Transaction em `SUBMITTED` com a exchange confirmando `FILLED` → `executed_at` no banco deve refletir o `updateTime` da exchange, não o momento do recovery.

---

## Critérios de aceitação

1. Após boot recovery de uma Transaction em `SUBMITTED` que a exchange confirma como `FILLED`, `executed_at` no banco reflete o `timestamp` do fill reportado pela exchange.
2. A reconciliação com janela cobrindo o timestamp real do fill encontra a Transaction como `MATCHED`, não `EXCHANGE_ONLY`.
3. Suite de testes passa: `./scripts/gradle-run.ps1 -q :core:test :spring-application:test`.

---

## Notas

- O bug é latente em produção: qualquer crash seguido de boot recovery pode gerar discrepâncias silenciosas na reconciliação se a janela consultada não cobrir o dia do recovery.
- A correção em banco para o dado atual (homologação): `UPDATE runner_transactions SET executed_at = '2026-06-12T17:58:01Z' WHERE exchange_order_id = '4026112';`
