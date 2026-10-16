# T39 — Auditoria Design × Código (capital, risco e concorrência)

**Complexidade:** Média (investigação; sem mudança de código)
**Responsável:** Claude
**Dependências:** nenhuma
**Status:** Concluído (2026-10-16) — ver "Matriz de aderência".

---

## Descrição

`docs/IMPLEMENTATION_GUIDE.md` e `docs/BLUEPRINT.md` estão sendo lidos como descrição do sistema
atual, mas parte deles é **intenção não implementada**. A T36 nasceu exatamente disso (safety buffer e
devolução de excedente §7.2.1–7.2.3 especificados e ausentes no código), e a avaliação de prontidão
para operação de 2026-10-15 encontrou outros casos da mesma classe:

| Seção do design | O que descreve | Estado no código (2026-10-15) |
|---|---|---|
| IG §12.1–12.2 (Actor/Mailbox, nota #23 marcada "RESOLVIDA") | Mailbox por runner, capacidade 1, processamento sequencial | Inexistente — zero ocorrências de mailbox em `src/main`; ticks processados no `messageProcessingExecutor` compartilhado (ver T42) |
| BLUEPRINT §11.1.A / IG ~l.3379 (`DrawdownMonitor`) | Halt automático por drawdown de 24h | Inexistente (ver T43) |
| BLUEPRINT §11.1.B.1 (níveis do Safe Mode) | `HALT`, `CANCEL_ALL`, `PANIC_SELL` com execução assíncrona | Só o enum `SafeModeStatus`; nenhum código executa `CANCEL_ALL`/`PANIC_SELL` e **nenhum código chama `Portfolio.setSafeModeStatus`** (ver T45) |
| `Position.updateCurrentPrice` | Marcação a mercado da posição | Método sem chamador: `currentPrice` fica igual ao `averagePrice` da abertura, então `unrealizedPnl` e `currentPnlPercent` do `StrategyContextDto` são sempre zero |
| T25 (spec) | "Ativação atual: manual via `PUT /api/admin/portfolios/{id}/safe-mode`" | Endpoint não existe |

Esta task fecha o padrão: varrer as seções de capital, risco e concorrência do IG/Blueprint e
classificar cada decisão como **implementada**, **pendente (com task)** ou **descartada (doc
corrigido)**, para que nenhuma decisão financeira fique só no papel.

---

## Escopo

Seções a auditar (no mínimo):

- IG §3 (Portfolio/Safe Mode), §6 (Runner), §7 (reserva de capital), §11 (circuit breakers), §12
  (concorrência/mailbox/backpressure), §13–14 (sinal e execução).
- BLUEPRINT §11 (defesa de capital, níveis de Safe Mode, retomada).
- `docs/PHASE0_INVARIANTS.md`: cada invariante tem teste que a protege?
- Javadocs que afirmam comportamento (ex.: `CapitalRequest` já citado na T36).

Fora do escopo: corrigir código. Cada divergência vira task própria ou correção de documento.

---

## Entregáveis

1. Seção "Matriz de aderência" neste arquivo: `seção → decisão → estado → evidência (arquivo/linha
   ou ausência verificada) → destino (task/correção doc/descartar)`.
2. Docs com divergência de "descartar" corrigidos no próprio PR (IG/Blueprint/Javadoc marcados ou
   reescritos).
3. Divergências de "pendente" com task criada em `.ai/tasks/` e linha no `docs/tasks/BACKLOG.md`.
4. Correção da afirmação factual errada da T25 sobre ativação manual do Safe Mode (feita em
   2026-10-15 com nota apontando para a T45; confirmar).

---

## Critérios de aceitação

1. Toda seção listada no escopo aparece na matriz com estado e evidência.
2. Nenhuma linha "pendente" sem task associada.
3. Nenhum documento do escopo afirma como implementado algo que a matriz classificou como pendente.

---

## Matriz de aderência (2026-10-16)

Estados: **Implementada**; **Outra forma** (implementada com desenho diferente — doc corrigido);
**Pendente** (task associada); **Descartada v1** (não será feita agora — doc marcado).

### IG §3 — Modelo de dados

| Seção | Decisão | Estado | Evidência | Destino |
|---|---|---|---|---|
| 3.2.1 | Portfolio com `safeModeStatus`, `capitalPoolingMode`, versão | Implementada | `Portfolio`, `portfolios` (V1, V13) | — |
| 3.2.2 | Semântica: `available = Total − Reserved − Dust`; realizado recebe a margem convertida | Outra forma | `GlobalBalance.confirmExecution`: `reserved −= custo`, `available += custo + pnl`, `realized += pnl` (contador já embutido em `available`) | IG corrigido |
| 3.2.3 | `MarginAccount` por exchange com `reservedCapital` | Descartada v1 | Classe de domínio sem repositório nem uso; exposição calculada por consulta (`RunnerExposureService`) | IG marcado |
| 3.2.4 | `DustAccount` (`ROUNDING_DUST`, `TECHNICAL_DEBT`) | Descartada v1 | Classe sem repositório; resíduo baixado via PnL na T46; fee em terceiro ativo só logada | IG marcado |
| 3.3.1 | `ExecutionPolicy` HEDGING/NETTING; `AccountingPolicy` LIFO/SPECIFIC_MATCH | Descartada v1 | Enums sem comportamento; `CreateRunnerUseCase` fixa FIFO; só SINGLE tem regra (`RunnerSignalPolicy`) | IG marcado |
| 3.3.1 | `maxOpenPositions` / `maxPendingOrders` como limites | Pendente | Campos persistidos, sem validação (só `maxOpenPositions` vai no contexto) | **T50** |
| 3.3.1 | `dedicatedBudget` | Descartada v1 | Campo sem uso (ver 7.5) | IG marcado |
| 3.3.1 | `lastReconciliationAt`, `archivedAt`, `isReconciling` | Implementada | `StrategyRunner.activate/completeReconciliation/archive`; cutoff em `PortfolioZombieDetectionUseCase` | — |
| 3.3.4 | `UNIQUE(buyTransactionId, sellTransactionId)` | Outra forma | V5 removeu: um match por fill (parciais geram vários) | IG corrigido |
| 3.4.1 | `ClientOrderId` versionado, roteamento por shortCode | Implementada | `ClientOrderId` | — |
| 3.4.2 | Fee cross-currency convertida por mark price; falha → `DustAccount` | Descartada v1 | T46: fee em terceiro ativo não suportada, log ERROR; BNB desligado no runbook | IG marcado |

### IG §6 — Ciclo de vida da transação

| Seção | Decisão | Estado | Evidência | Destino |
|---|---|---|---|---|
| 6.1 | Máquina de estados monotônica | Implementada | `Transaction`, `TransactionInvariantsTest`, `MockTerminalMonotonicityStabilityIntegrationTest` | — |
| 6.1.1 | PARTIAL de BUY converte reserva proporcional (`confirmExecution`) | Outra forma | Custo da BUY fica em `reserved` até a venda; só a SELL confirma (T36) | IG corrigido |
| 6.2 | Persist-first: persist + reserva na mesma transação; dispatch fora | Implementada | `ProcessTradeSignalUseCase.persistBuyAndReserve` + `TransactionTemplate` (`RunnerConfig`) | Javadoc corrigido (dizia que o PENDING ficava gravado) |
| 6.3.2 | Pós-timeout: SUBMITTED otimista + GET imediato com backoff 5/10/20/60s | Outra forma | Falha no dispatch deixa PENDING; `RunnerTransactionRecoveryWatchdog` consulta antes de expirar (`runner.recovery.transaction.*`) | IG corrigido |
| 6.3.3 / 6.8 | `runner.watchdog.*`, `runner.dispatch.*`; SUBMITTED estourado → cancel | Descartada v1 | Chaves inexistentes; ordens LIMIT podem descansar por design (T32–T35); o watchdog consulta, não cancela | IG marcado |
| 6.4 | Contabilidade incremental por fill; PARTIAL→CANCELED devolve o não executado | Implementada | `FillCalculator`, `SellFillHandler`, `MarginReleaseBuilder` (T36) | — |
| 6.5 | `clientOrderId` como chave de idempotência | Implementada | `ClientOrderId`, conciliação por `clientOrderId` | — |
| 6.6 | Boot: PENDING sem `exchangeOrderId` expira direto | Outra forma | Consulta antes de expirar, com `pending-grace-ms` (T31, phase3) | IG corrigido |
| 6.7 | Key pooling, rate limit por peso, fila de prioridade, failover | Descartada v1 / Pendente parcial | Inexistente; conta única. Rate limit por peso registrado na **T24** | IG marcado; T24 |

### IG §7 — Capital e margem

| Seção | Decisão | Estado | Evidência | Destino |
|---|---|---|---|---|
| 7.1 passo 1 | Safe Mode bloqueia reserva | Implementada | `CapitalReservationPolicy` | — |
| 7.1 passo 2 | Runner não ativo → `RUNNER_NOT_ACTIVE` | Outra forma | `canAcceptSignals` relido na transação (`RunnerHaltedException`) | IG corrigido |
| 7.1 passo 3 | Idempotência da reserva por `transactionId` | Descartada v1 | Reserva ocorre na mesma transação do persist; não há reentrada | IG marcado |
| 7.1 passo 4 | `MAX_POSITIONS_REACHED`, `MAX_PENDING_REACHED` | Pendente | Inexistentes | **T50** |
| 7.2 | Sem buffer para LIMIT; buffer obrigatório para BUY MARKET | Implementada | T36; T45 trouxe só SELL MARKET (sem reserva) | IG esclarecido |
| 7.3 V2 | Confiança mínima do sinal | Descartada v1 | Inexistente; filtro é responsabilidade da estratégia | IG marcado |
| 7.3 V3/V4 | `minQty`/`minNotional` no runner | Outra forma | Normalização ao `stepSize` no core; `OrderFilterValidator` rejeita no adapter Binance | IG corrigido |
| 7.3 V5 | Sanidade de preço (desvio > 10% do último preço) | Pendente | Inexistente — crítico com estratégia remota escolhendo `limitPrice` | **T50** |
| 7.4.1 | Max cancel rate (soft) | Pendente | Inexistente | **T51** |
| 7.4.4 / 13.2.2 | Limites mutáveis via API admin | Pendente | Sem endpoint | **T49** |
| 7.5 | Pooling DEDICATED | Descartada v1 | Sem comportamento | IG marcado |
| 7.6.2 | Serialização da reserva com `SELECT … FOR UPDATE` | Outra forma | `UPDATE … WHERE available_balance >= :amount` atômico (`GlobalBalanceJdbcRepository.reserveAtomic`) | IG corrigido |
| 7.7 | Métricas de capital (rejection rate, utilização, duração da reserva) | Pendente | Só `signal.evaluated.total` | **T51** |
| 7.9 | `capital.*`, `runner.min-confidence…`, `monitoring.*` | Descartada v1 / Pendente | Chaves inexistentes; as de T50/T51 nascem nessas tasks | IG marcado |
| BLUEPRINT 11.2.D | Gate de utilização global de margem (ex.: 90%) | Pendente | Inexistente | **T50** (decisão de negócio) |

### IG §11 / BLUEPRINT §11 — Circuit breaker e Safe Mode

| Seção | Decisão | Estado | Evidência | Destino |
|---|---|---|---|---|
| BP 11.1.A-1 / IG 11.1 | Drawdown 24h → HALT automático | Implementada | T43 `EvaluateDrawdownUseCase` | — |
| BP 11.1.A-2 / IG 11.6 | Rejeições consecutivas → HALT do runner, retomada após cooldown | Pendente | Inexistente | **T25** (escopo ampliado) |
| BP 11.1.A-3 | Divergência crítica de saldo (`FAIL_DEFICIT`) → HALT | Pendente | Boot só loga (ou falha em `FAIL_FAST`); não aciona Safe Mode | **T25** |
| BP 11.1.A-4 / IG 11.5.3 | Anomalia de latência → HALT, warm-up automático | Pendente | Inexistente | **T25** |
| BP 11.1.B.1 / IG 11.2–11.3 | Níveis HALT/CANCEL_ALL/PANIC_SELL | Implementada (T45) | `ManageSafeModeUseCase`, `SafeModeHandler`; HALT ainda permite SELL; execução por tick | IG corrigido |
| IG 11.4.1 | `POST /api/admin/safe-mode` global; proíbe pular na subida, permite pular na descida | Outra forma | `PUT /api/admin/portfolios/{id}/safe-mode`; regra inversa, alinhada ao BP 11.1.C | IG corrigido |
| IG 11.4.3 | `/actuator/health` inclui o Safe Mode | Pendente | Sem `HealthIndicator` | **T51** |
| IG 11.5.2 | Reset exige zero ordens em voo ao sair de CANCEL_ALL/PANIC_SELL | Descartada v1 | Descida um nível por vez + status com o que falta convergir (T45) | IG marcado |
| IG 11.7 | `circuit_breaker.current_level`, `.duration.active`, `.runner.suspended.count` | Pendente | Só drawdown/peak/equity/activations (T43) | **T51** |

### IG §12 — Concorrência

| Seção | Decisão | Estado | Evidência | Destino |
|---|---|---|---|---|
| 12.1–12.2, 12.6 | Mailbox por runner (capacidade 1), thread dedicada | Pendente | Ticks no `messageProcessingExecutor` compartilhado | **T42** |
| 12.3 | Stale signal check | Pendente | Inexistente | **T40** (idade da decisão) / **T42** |
| 12.4 | SINGLE rejeita SELL com ordem em voo | Outra forma | SINGLE só barra BUY; SELL concorrente barrada pelo lock de posição | IG corrigido |
| 12.5 | Comandos admin com prioridade (FORCE_CANCEL, FORCE_CLOSE_POSITION, RESUME) | Pendente | Só halt (T8) e Safe Mode (T45) | **T49** |
| 12.7 | Timeout por fase do pipeline | Pendente | Só timeouts de HTTP/cliente | **T42** |
| 12.8 | Métricas de backpressure | Pendente | Inexistentes | **T42** |
| 12.9 | Rate limiter por runner no ExchangeAdapter | Pendente | Inexistente | **T24** |

### IG §13 — Ciclo de vida do runner

| Seção | Decisão | Estado | Evidência | Destino |
|---|---|---|---|---|
| 13.1 | Máquina de estados do runner | Implementada | `StrategyRunner`, `StrategyRunnerInvariantsTest` | — |
| 13.1 / 13.6 | `start`, `resume`, `terminate`, `PATCH` por API | **Pendente (crítico)** | `resume()`/`startTerminating()`/`archive()` sem chamador; runner criado só fica ACTIVE no próximo boot; runner halted não volta sem editar o banco | **T49** |
| 13.3 | Health check antes de ativar/retomar | Pendente | Inexistente | **T49** |
| 13.4 | Encerramento com posições abertas + worker TERMINATING→ARCHIVED | Pendente | Inexistente | **T49** |
| 13.5 | Soft delete com `archivedAt` | Implementada (domínio) | `StrategyRunner.archive`; sem fluxo que o acione | T49 |
| 13.7 | Métricas de lifecycle | Pendente | Inexistentes | **T51** |
| 13.8 | `portfolio.max-runners` | Descartada v1 | Inexistente; operação com poucos runners | IG marcado |

### IG §14 — Contrato da strategy

| Seção | Decisão | Estado | Evidência | Destino |
|---|---|---|---|---|
| 14.2–14.6 | `StrategyContextDto` montado pelo runner | Implementada | `RunnerContextAssembler`; flutuante real desde a T43 | — |
| 14.7 / 14.8 | Estratégia não vê `totalCapital` | Pendente | Contexto envia `totalCapital` | **T40** |
| 14.5.2 | `orderType` "quando LIMIT existir" | Outra forma | LIMIT é o padrão (T34 `limitPrice`); MARKET só na liquidação (T45); proteção LIMIT na **T48** | IG corrigido |
| 14.9 / 14.10 | Cooldown implícito; `runner.context.*`, `runner.cooldown.explicit-ms` | Descartada v1 | Implícito via SINGLE; chaves inexistentes | IG marcado |
| 14.11 | Estratégia stateless | Pendente | Warm-up do SMA por instância | **T40** |

### `docs/PHASE0_INVARIANTS.md`

| Invariante | Estado | Teste que protege |
|---|---|---|
| 1. Capital (saldos ≥ 0; reserva única; liberação sem duplicar) | Implementada | `GlobalBalanceInvariantsTest`, `CapitalDeadLetterReplayIntegrationTest`, `ScenarioImmediateRoundTripE2ETest` (`reserved = 0`) |
| 2. Transação monotônica; evento repetido sem efeito | Implementada | `TransactionInvariantsTest`, `MockTerminalMonotonicityStabilityIntegrationTest`, `MockTerminalOrderOverrideIntegrationTest` |
| 3. Position/Lot (`opened_by` obrigatório; SELL ≤ disponível; lock exclusivo) | Implementada | V6 + `MissingPositionOriginException`; `Position.reduceQuantity`; `ConcurrentSellLockIntegrationTest` |
| 4. Match e PnL sem duplicar; PnL coerente com fees | Implementada | `MockSellOrderOverrideIntegrationTest`, `UserDataStreamConciliationIntegrationTest`, `FeeAccountingConciliationTest` |
| 5. Idempotência (mesmo update não reaplica; PARTIAL→FILLED só delta) | Implementada | `ConciliationOrderUpdateIdempotencyTest`, `MockBuyOrderOverrideIntegrationTest` |
| 6. Boot mínimo (WARN_ONLY não bloqueia; FAIL_FAST registra fase) | Implementada | `BootOrchestratorBootMinimumTest` |

### Javadocs que afirmavam comportamento

| Local | Afirmação | Estado | Destino |
|---|---|---|---|
| `ProcessTradeSignalUseCase.persistBuyAndReserve` | "a transação PENDING fica gravada… expirada pelo watchdog" quando a reserva falha | Errada: a transação faz rollback | Javadoc corrigido |
| `Position.reduceQuantity` | fee não convertida vai para `DustAccount` como `TECHNICAL_DEBT` | Não existe | Javadoc corrigido |
| `CapitalRequest` / `GlobalBalanceRepositoryPort` | buffer de segurança | Corrigidos na T36 | — |
| T25 (spec) | ativação manual do Safe Mode por API | Corrigida em 2026-10-15; API entregue na T45 | — |

### Resumo

- **Tasks novas:** T49 (ciclo de vida do runner por API — crítico para operar), T50 (limites de risco
  pré-trade), T51 (observabilidade de capital, Safe Mode e lifecycle).
- **Tasks com escopo ampliado:** T25 (gatilhos 2–4 do BP 11.1.A e circuit breaker por runner), T40
  (`totalCapital` no contexto), T42 (timeouts por fase), T24 (rate limit por peso).
- **Docs corrigidos:** IG §3, §6, §7, §11, §12, §13, §14 com nota de estado por seção; legenda das
  notas de implementação; dois Javadocs; runbook e spec da T45 (a reativação de runner halted não existe).

---

## Fontes

- `.ai/tasks/T36-capital-reservation-safety-buffer.md` (caso que originou o padrão).
- `docs/IMPLEMENTATION_GUIDE.md`, `docs/BLUEPRINT.md`, `docs/PHASE0_INVARIANTS.md`.
- `core/src/main/java/com/marmitt/core/enums/SafeModeStatus.java`,
  `core/src/main/java/com/marmitt/core/domain/runner/Position.java` (`updateCurrentPrice`),
  `spring-application/src/main/java/com/marmitt/application/spring/config/AsyncConfig.java`.
