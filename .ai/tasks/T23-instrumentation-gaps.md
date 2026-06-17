# T23 — Instrumentação: Gaps Críticos de Observabilidade

**Complexidade:** Média  
**Responsável:** Claude  
**Dependências:** T21 (Discord + DLQ gauge), T22 (monitoring-spec.md como referência)  
**Status:** Pendente

---

## Descrição

O estudo T22 (`/.ai/monitoring-spec.md`) identificou blind spots que não podem ser cobertos por configuração de Grafana nem por logs existentes — requerem instrumentação nova na aplicação. Esta tarefa resolve os gaps críticos que deixam fluxos inteiros invisíveis ao monitoramento.

---

## Escopo

### G1 — Gauge de estado da conexão WebSocket

**Problema:** WebSocket pode estar em estado OPEN mas sem entregar mensagens (conexão ghost). Nenhuma métrica expõe isso hoje.

**Solução:** Registrar um gauge `websocket.connection.state` via Micrometer.

- Tags: `exchange` (BINANCE, MOCK), `channel` (MARKET, USER_DATA)
- Valor: `1` = conectado, `0` = desconectado/falha
- Onde atualizar: `ConnectionStateEventListener` nos eventos `WebSocketConnectedEvent` e `WebSocketFailedEvent`/`WebSocketClosedEvent`
- Permite detectar: "conectado mas silencioso" combinando gauge=1 com ausência de logs de mensagem

---

### G2 — Counter de sinais avaliados por decisão

**Problema:** Runner pode estar recebendo ticks e sempre retornando HOLD sem gerar nenhum log além do tick recebido. Impossível distinguir "runner funcionando, mercado lateral" de "runner travado em HOLD".

**Solução:** Counter `signal.evaluated.total` via Micrometer.

- Tags: `runnerId`, `decision` (HOLD, BUY, SELL, REJECTED_CAPITAL, REJECTED_LOCK)
- Onde incrementar: `ProcessTradeSignalUseCase`, após cada avaliação de sinal
- `REJECTED_CAPITAL` = sinal BUY descartado por `CapitalReservationRejectedException`
- `REJECTED_LOCK` = sinal SELL descartado por `ConcurrentPositionLockException`
- Permite detectar: runner sempre em HOLD, falhas silenciosas de reserva de capital

---

### G3 — Log estruturado de runner HALTED por DLQ no boot recovery

**Problema:** Quando `RunnerBootRecoveryUseCase` decide HALTAR um runner por DLQ pendente, não há log estruturado explícito — o operador só descobre consultando o estado do runner via API.

**Solução:** Adicionar log sentinel em `RunnerBootRecoveryUseCase` na decisão de HALT.

```java
log.warn("bootRecovery: runner halted runnerId={} reason=DLQ_PENDING portfolioId={}",
        ctx.runnerId(), ctx.portfolioId());
```

- Permite alerta via LogQL: `{app="ctrade"} |= "bootRecovery: runner halted"`
- Trigger: `#alerts-error` (runner parado afeta operação diretamente)

---

### G4 — Campos estruturados nos logs das fases 2 do boot

**Problema:** Os logs de conclusão das subfases do boot não têm campos que permitam correlação ou detecção de anomalia. (Listado também em T21 Camada 3 — consolidar aqui a implementação.)

**Solução:** Enriquecer os logs em `RunBootSequenceUseCase`:

| Log atual | Log proposto |
|---|---|
| `bootSequence.phase1: completed exchanges={}` | + `runId={}` |
| `bootSequence.phase2.sanity: completed` | + `runId={} result={PASS/WARN_SURPLUS/FAIL_DEFICIT/SKIPPED/FAILED} portfolios={}` |
| `bootSequence.phase2.zombie: completed` | + `runId={} detected={} mode={FAIL_FAST/WARN_ONLY}` |
| `bootSequence.phase2.ttl: completed` | + `runId={} expired={}` |

---

### G5 — Log de capital event ignorado por idempotência

**Problema:** Quando um `ExecutionConfirmedEvent` ou `MarginReleaseEvent` é descartado por idempotência (match ID já processado), nenhum log é emitido. Replays inesperados passam invisíveis.

**Solução:** Adicionar log em `ExecutionConfirmedReaction` e `MarginReleasedReaction` quando idempotência rejeita o evento:

```java
log.info("executionConfirmedReaction: duplicate ignored matchId={} transactionId={}", matchId, transactionId);
log.info("marginReleasedReaction: duplicate ignored transactionId={}", transactionId);
```

- Permite detectar: replays inesperados de eventos de capital
- Permite distinguir: "evento processado normalmente" de "evento silenciado"

---

## Alertas habilitados após esta task

| Indicador novo | Query | Trigger |
|---|---|---|
| WebSocket ghost | `websocket_connection_state{channel="USER_DATA"} == 1` AND ausência de `executionReport` | `#alerts-behavior` |
| Runner sempre HOLD | `increase(signal_evaluated_total{decision="HOLD"}[1h]) > 0` AND `increase(signal_evaluated_total{decision=~"BUY|SELL"}[1h]) == 0` | `#alerts-behavior` |
| Runner HALTED por DLQ | `{app="ctrade"} \|= "bootRecovery: runner halted"` | `#alerts-error` |
| Sanity anômalo | `{app="ctrade"} \|= "bootSequence.phase2.sanity" \| json \| result=~"WARN_SURPLUS\|FAIL_DEFICIT\|FAILED"` | `#alerts-behavior` |
| Zombies detectados em WARN_ONLY | `{app="ctrade"} \|= "bootSequence.phase2.zombie" \| json \| detected > 0 \| mode="WARN_ONLY"` | `#alerts-behavior` |

---

## Arquivos a modificar

| Arquivo | Mudança |
|---|---|
| `spring-application/.../handler/ConnectionStateEventListener.java` | Registrar gauge `websocket.connection.state` via `MeterRegistry` (G1) |
| `core/.../usecase/runner/processsignal/ProcessTradeSignalUseCase.java` | Incrementar counter `signal.evaluated.total` com tag `decision` (G2) |
| `core/.../usecase/runner/RunnerBootRecoveryUseCase.java` | Log warn ao HALTAR runner por DLQ (G3) + enriquecer log de conclusão se `runId` disponível no contexto |
| `core/.../usecase/boot/RunBootSequenceUseCase.java` | Adicionar `runId` e campos de resultado nos logs das fases 2 (G4) |
| `core/.../application/reaction/ExecutionConfirmedReaction.java` | Log de evento ignorado por idempotência (G5) |
| `core/.../application/reaction/MarginReleasedReaction.java` | Log de evento ignorado por idempotência (G5) |

---

## Critérios de aceitação

1. Métrica `websocket_connection_state{exchange,channel}` visível no Prometheus após boot.
2. Métrica `signal_evaluated_total{runnerId,decision}` incrementando a cada sinal processado pelo mock.
3. Ao forçar runner HALTED no boot (via DLQ pendente), log `bootRecovery: runner halted` aparece no Loki.
4. Logs das fases 2 do boot contêm `runId` e campos de resultado consultáveis via LogQL.
5. Evento de capital duplicado produz log `duplicate ignored` no Loki.
6. Nenhum dos cambios acima altera comportamento funcional — só adiciona observabilidade.
