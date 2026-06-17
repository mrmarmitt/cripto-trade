# Monitoring Spec — ctrade

**Gerado a partir de:** `/.ai/flows/` + análise de logs sentinel existentes (T21)  
**Status:** Rascunho — calibrar thresholds em staging antes de ativar alertas

---

## Tabela Mestre de Monitoramento

| # | Fluxo | Indicador | Tipo | Query (LogQL/PromQL) | Janela | Condição de disparo | Canal | Prioridade |
|---|---|---|---|---|---|---|---|---|
| M1 | Boot | Boot não concluído | Log ausente | `absent_over_time({app="ctrade"} \|= "bootSequence: completed" [10m])` | 10 min desde startup | Ausência | `#alerts-error` | Alta |
| M2 | Boot | Runner com erros no recovery | Log present | `{app="ctrade"} \|= "bootRecovery: completed" \| json \| hasErrors="true"` | On occurrence | Presença | `#alerts-behavior` | Alta |
| M3 | Boot | Boot fail-fast | Event (T21) | Discord webhook via `BootAlertListener` | On occurrence | Presença | `#alerts-error` | Alta |
| M4 | WebSocket | Reconexões frequentes | Log rate | `rate({app="ctrade"} \|= "reconnect" [5m]) > 0.05` | 5 min | > 3 reconexões/min | `#alerts-behavior` | Média |
| M5 | WebSocket | Spike de ERRORs | Log rate | `rate({app="ctrade", level="ERROR"} [1m]) > 0.1` | 1 min | > 0.1 ERRORs/s | `#alerts-behavior` | Média |
| M6 | WebSocket | Alta taxa de WARNs | Log rate | `rate({app="ctrade", level="WARN"} [5m]) > 0.5` | 5 min | > 0.5 WARNs/s por 2 min | `#alerts-behavior` | Baixa |
| M7 | Order Conciliation | Nenhuma ordem submetida | Log ausente | `absent_over_time({app="ctrade"} \|= "PENDING->SUBMITTED" [30m])` | 30 min | Ausência após atividade recente | `#alerts-behavior` | Média |
| M8 | Order Conciliation | Nenhum fill confirmado | Log ausente | `absent_over_time({app="ctrade"} \|= "executionConfirmedReaction:" \| json \| isFinal="true" [2h])` | 2 h | Ausência com posições abertas | `#alerts-behavior` | Baixa |
| M9 | Order Conciliation | Nenhuma margem liberada | Log ausente | `absent_over_time({app="ctrade"} \|= "marginReleasedReaction:" [2h])` | 2 h | Ausência com transações terminadas | `#alerts-behavior` | Baixa |
| M10 | Signal Processing | Nenhuma reserva de capital | Log ausente | `absent_over_time({app="ctrade"} \|= "capital reserved" [1h])` | 1 h | Ausência com runners ativos | `#alerts-behavior` | Baixa |
| M11 | Capital / DLQ | Nova entrada DLQ de capital | Event (T21) | Discord webhook via `CapitalEventListener.@Recover` | On occurrence | Presença | `#alerts-error` | Alta |
| M12 | Capital / DLQ | DLQ acumulada sem resolução | Métrica (gap) | `dlq_unresolved_total > 0` (gauge planejado em T21) | Contínuo | > 0 por mais de 30 min | `#alerts-error` | Alta |
| M13 | Dead Letter | Replay não aplicado | Log | `{app="ctrade"} \|= "deadLetter:" \| json \| applied="false"` | On occurrence | Presença | `#alerts-behavior` | Média |
| M14 | Binance Stream | Sem execução report após ordem | Log correlacionado | *(gap — requer instrumentação)* | — | — | — | Alta |

---

## Análise por Fluxo

### 1. Boot Recovery

**O que pode parar silenciosamente:**
- Boot completa com `hasErrors=true` em modo `WARN_ONLY` — runner continua ACTIVE mas com inconsistências
- Phase 2 detecta zombies mas não bloqueia (WARN_ONLY) — ordens abertas na exchange sem conciliação local
- Phase 2 sanity check retorna `WARN_SURPLUS` — saldo na exchange maior que o local

**Cadência esperada:** One-shot por startup. Deve concluir em < 3 min.

**Logs sentinel disponíveis:**
- `bootSequence: completed runId={} portfolios={} runners={}` — qualidade alta (M1)
- `bootRecovery: completed runnerId={} hasErrors={}` — qualidade alta, `hasErrors` é a chave (M2)
- `bootSequence.phase2.sanity: completed` — **qualidade baixa, sem campos** (melhoria pendente)
- `bootSequence.phase2.zombie: completed` — **qualidade baixa, sem campos** (melhoria pendente)
- `bootSequence.phase2.ttl: completed` — **qualidade baixa, sem campos** (melhoria pendente)

**Gaps de instrumentação:**
- Resultado do sanity check (PASS/WARN_SURPLUS/FAIL_DEFICIT/SKIPPED/FAILED) não está no log de conclusão da fase
- Contagem de zombies detectados em WARN_ONLY não está no log de conclusão da fase
- Count de transações expiradas por TTL não está no log de conclusão da fase

---

### 2. WebSocket Event Routing

**O que pode parar silenciosamente:**
- WebSocket em estado OPEN mas sem mensagens chegando (exchange silenciosa ou conexão ghost)
- Listener individual com exception repetida — erro logado por ocorrência mas sem rate tracking
- Market stream conectado mas nenhum tick entregue ao `ProcessTradeSignalUseCase`

**Cadência esperada:** Mensagens de mercado contínuas enquanto a exchange opera. Reconexões devem ser raras (< 1 por hora em operação normal).

**Logs sentinel disponíveis:**
- `Processing SUCCESS - type={}` — **qualidade baixa** (só tipo da classe, sem correlationId ou exchange) (melhoria pendente)

**Gaps de instrumentação:**
- Nenhum counter de mensagens processadas por exchange/canal — impossível detectar "conectado mas silencioso"
- Nenhum rate de erros por listener individual
- Estado da conexão WebSocket (OPEN/CLOSED/RECONNECTING) não exposto como métrica

---

### 3. Runner Signal Processing

**O que pode parar silenciosamente:**
- Runner recebendo ticks mas estratégia sempre retornando HOLD — nenhuma ordem gerada
- `CapitalReservationRejectedException` sendo descartada silenciosamente (sinal descartado sem trace)
- `ConcurrentPositionLockException` em SELL — sinal descartado, posição não vendida

**Cadência esperada:** Depende da estratégia. Em staging com dados sintéticos, BUY/SELL podem ser infrequentes. Não monitorar ausência de sinais em janelas curtas.

**Logs sentinel disponíveis:**
- `capital reserved transactionId={} runnerId={} amount={} portfolioId={}` — qualidade alta (M10)

**Gaps de instrumentação:**
- Nenhum log para "sinal avaliado: HOLD" — impossível distinguir runner processando vs. parado
- Nenhum log para "capital reservation rejected reason={}" — falhas silenciosas de reserva
- Nenhum log para `ConcurrentPositionLockException` com contexto (runnerId, positionId)

---

### 4. Order Conciliation

**O que pode parar silenciosamente:**
- Transação `PENDING` que nunca transita para `SUBMITTED` (ordem enviada mas callback NEW perdido)
- Transação `SUBMITTED` que nunca recebe fill (ordem aberta indefinidamente)
- Posição travada por SELL que nunca destravou (fill final não chegou)

**Cadência esperada:** Após `capital reserved`, `PENDING->SUBMITTED` deve aparecer em segundos. Fills dependem de mercado.

**Logs sentinel disponíveis:**
- `PENDING->SUBMITTED transactionId={} exchangeOrderId={}` — qualidade alta (M7)
- `BUY fill positionId={} runnerId={} increment={} fillPrice={}` — qualidade alta
- `SELL match persisted matchId={} transactionId={} status={} executedQty={}` — qualidade alta
- `executionConfirmedReaction: matchId={} transactionId={} totalCost={} fee={} isFinal={}` — qualidade alta (M8)
- `marginReleasedReaction: transactionId={} releaseAmount={} reason={} portfolioId={}` — qualidade alta (M9)

**Gaps de instrumentação:**
- Nenhum counter de transações por status atual (PENDING/SUBMITTED age não rastreada)
- Correlação entre `capital reserved` e `PENDING->SUBMITTED` não é detectável via LogQL puro (requer join por transactionId com `line_format`)

---

### 5. Portfolio Capital

**O que pode parar silenciosamente:**
- `MarginReleaseEvent` em retry loop infinito (max-attempts=`Integer.MAX_VALUE`) sem chegar a DLQ
- `GlobalBalance.availableBalance` driftando de expectativa sem que nenhum ERROR ocorra
- Idempotência de capital events silenciando um evento que deveria ter efeito

**Cadência esperada:** `ExecutionConfirmedEvent` para cada SELL fill; `MarginReleaseEvent` para cada CANCELED/EXPIRED/REJECTED.

**Logs sentinel disponíveis:**
- `executionConfirmedReaction:` — qualidade alta (M8)
- `marginReleasedReaction:` — qualidade alta (M9)
- `CAPITAL_DLQ_PERSISTED:` — qualidade alta, indica falha terminal (M11)

**Gaps de instrumentação:**
- `availableBalance`, `reservedBalance`, `realizedBalance` não expostos como gauge Micrometer — impossível alertar em drift de saldo
- Retry count de `MarginReleaseEvent` não é observável externamente (loop silencioso)
- Nenhum log para "capital event marked as duplicate (idempotency)" — útil para detectar replays inesperados

---

### 6. Binance User Data Stream

**O que pode parar silenciosamente:**
- Subscription enviada mas confirmação com status != 200 (log de erro, mas stream não para)
- Stream conectado e subscrito mas sem `executionReport` chegando após ordem colocada
- Reconnect bem-sucedido mas subscription não renovada (timestamp vencido rejeitado silenciosamente)

**Cadência esperada:** Após cada ordem colocada, `executionReport` com `X=NEW` deve chegar em < 5s. Fills dependem de mercado.

**Logs sentinel disponíveis:**
- Nenhum sentinel de alta qualidade para "subscription confirmada" (a confirmação retorna `ProcessingResult.error()` por design — não notifica listeners)
- Eventos de `executionReport` chegam como `OrderDataDto` mas o log está em `order-conciliation` (`PENDING->SUBMITTED`)

**Gaps de instrumentação (críticos):**
- Nenhum indicador de "subscription ativa e saudável" — o stream pode estar conectado sem receber nada
- Correlação entre `capital reserved` (ordem enviada) e `PENDING->SUBMITTED` (NEW recebido) exige lógica fora do Loki
- Nenhum counter de `executionReport` recebidos por runnerId

---

### 7. Dead Letter Replay

**O que pode parar silenciosamente:**
- DLQ entries acumulando sem notificação (principal blind spot — resolvido por T21)
- Replay retornando `applied=false` — entry permanece aberta, operador não é avisado
- Runner em estado HALTED por DLQ — operação parada mas sem alerta ativo

**Cadência esperada:** DLQ deve estar zerada em operação normal. Qualquer entrada não-zero é sinal de ação necessária.

**Logs sentinel disponíveis:**
- `deadLetter: reprocessed id={} requestedBy={}` — qualidade alta
- `deadLetter: resolved id={} resolvedBy={}` — qualidade alta
- `CAPITAL_DLQ_PERSISTED: dlqId={}` — qualidade alta (M11)

**Gaps de instrumentação:**
- `dlq.unresolved.total` gauge ainda não existe (planejado em T21 Camada 1)
- Nenhum log explícito quando boot recovery decide HALTAR um runner por DLQ pendente
- Replay `applied=false` é logado internamente mas sem campo estruturado `applied` para query (melhoria pendente)

---

## Gaps de Instrumentação (insumos para tasks futuras)

### Gaps de log (sem nova classe, só adicionar campo)

| Arquivo | Log atual | Campo faltante | Impacto |
|---|---|---|---|
| `RunBootSequenceUseCase` | `bootSequence.phase2.sanity: completed` | `runId`, `result` (PASS/WARN/FAIL/SKIP) | Detectar sanity anômalo |
| `RunBootSequenceUseCase` | `bootSequence.phase2.zombie: completed` | `runId`, `detected` (count), `mode` (FAIL_FAST/WARN_ONLY) | Detectar zombies em WARN_ONLY |
| `RunBootSequenceUseCase` | `bootSequence.phase2.ttl: completed` | `runId`, `expired` (count) | Rastrear expiração de reservas |
| `RunBootSequenceUseCase` | `bootSequence.phase1: completed exchanges={}` | `runId` | Correlacionar fases pelo mesmo boot |
| `ProcessMessageEventListener` | `Processing SUCCESS - type={}` | `correlationId`, `exchange` | Detectar "conectado mas silencioso" por exchange |
| `ManageDeadLetterUseCase` | DLQ replay internamente | `applied=true/false` como campo estruturado | Detectar replays não aplicados via LogQL (M13) |
| `RunnerBootRecoveryUseCase` | Runner HALTED por DLQ | Log explícito `bootRecovery: runner halted runnerId={} reason=DLQ_PENDING` | Detectar runners parados |

### Gaps de métrica (requerem Micrometer)

| Métrica | Tipo | Onde registrar | Impacto |
|---|---|---|---|
| `dlq.unresolved.total` | Gauge | `JdbcDeadLetterEntryRepositoryAdapter` | Alertar em DLQ acumulada (M12) — planejado em T21 |
| `capital.event.retry.count{eventType}` | Counter | `CapitalEventListener` retry intercept | Detectar retry loop silencioso de margem |
| `websocket.connection.state{exchange,channel}` | Gauge (0/1) | `ConnectionStateEventListener` | Detectar "conectado mas silencioso" |
| `portfolio.balance.available{portfolioId}` | Gauge | `ExecutionConfirmedReaction` / `MarginReleasedReaction` | Alertar em drift de saldo |
| `signal.evaluated.total{runnerId,decision}` | Counter | `ProcessTradeSignalUseCase` | Detectar runner sempre em HOLD |

### Gaps de lógica de correlação (requerem código de monitoramento)

| Correlação | Descrição | Complexidade |
|---|---|---|
| `capital reserved` → `PENDING->SUBMITTED` em < 2 min | Detectar ordem enviada mas sem callback NEW | Alta — requer join por transactionId com timeout |
| Subscription Binance confirmada → `executionReport` em < 60s após ordem | Detectar stream saudável por end-to-end | Alta — requer rastrear estado da sessão |

---

## Resumo de Prioridades de Implementação

### Implementar agora (T21 já cobre ou cobre com mínimo esforço)
- M1, M2, M3 — Boot alerts
- M4, M5, M6 — Rate alerts via Grafana + Discord contact point
- M11 — DLQ capital via Discord webhook (T21 Camada 1)
- M12 — DLQ gauge (T21 Camada 1, 3 linhas de Micrometer)

### Implementar após calibração em staging
- M7, M8, M9, M10 — Alertas de ausência (thresholds dependem do volume real observado)
- M13 — Replay não aplicado (requer melhoria de log em `ManageDeadLetterUseCase`)

### Requer task própria (gaps de instrumentação críticos)
- `websocket.connection.state` gauge — detectar "conectado mas silencioso"
- `signal.evaluated.total{decision=HOLD}` counter — detectar runner travado
- Log de runner HALTED por DLQ no boot recovery
- Campos estruturados nas fases 2 do boot (já listados em T21 Camada 3)
