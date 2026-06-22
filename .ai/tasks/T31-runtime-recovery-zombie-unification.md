# T31 — Unificação do Recovery de Runtime: cobertura de zombies (`PENDING`)

**Complexidade:** Baixa  
**Responsável:** Claude  
**Dependências:** Nenhuma  
**Status:** Implementado (Opção 1 + dedup do boot). Observabilidade dedicada **adiada** (ver "Fora de escopo / Adiado").

---

## Descrição

Fecha o furo da reserva órfã (`PENDING` sem `exchangeOrderId`, "zombie") em runtime **sem criar fluxo novo**: estende o recovery de runtime existente para cobrir `PENDING` com **query-before-expire**, e **remove a expiração-cega de zombie do boot** (que duplicava o terminal-fallback do engine).

O motor por-transação (`RecoverTransactionStatusUseCase`) já fazia "consulta-e-concilia" e já aceitava `PENDING`; o único gap era o seletor de lote (`RecoverStaleTransactionsUseCase`), que filtrava só `SUBMITTED`/`PARTIAL`.

---

## O que foi implementado (Opção 1)

### 1. Runtime — `RecoverStaleTransactionsUseCase` passa a cobrir `PENDING`

- `ELIGIBLE_STATUSES` agora é `[PENDING, SUBMITTED, PARTIAL]`.
- O lote passa **um único** request (`forRuntimeWatchdog` = `MissingOrderPolicy.DERIVE_FROM_STATUS`); a política de not-found é **resolvida dentro do engine pelo status RECARREGADO** da transação:
  - `PENDING` (nunca confirmado) → terminal fallback (`EXPIRED`/`CANCELED`, **sem DLQ**);
  - `SUBMITTED`/`PARTIAL` (confirmado) → `REGISTER_DLQ`.
- Resolver no engine (e não no snapshot do lote) **elimina a corrida**: se a linha virar `SUBMITTED`/`PARTIAL` entre a seleção e o `execute`, o not-found vira DLQ, não expiração indevida de ordem confirmada.
- **Cutoff único reutilizado** (`runner.recovery.transaction.stale-threshold-ms`). Não foi criado watchdog, properties nem use case dedicado.

### 2. Boot — `RunnerBootRecoveryUseCase` deixa de tratar zombie separadamente

- Removido o **Step 3 (`step3ExpireZombies`)** e a classificação zombie/limbo do **Step 1**.
- `PENDING` agora entra no **mesmo caminho de reconciliação** do antigo limbo (Step 4), via `RecoverTransactionStatusUseCase.forBoot` → **query-before-expire** também no boot (não há mais expiração local cega nem carência de TTL).
- Código removido por ser usado **apenas** pelo zombie:
  - método `buildSyntheticTerminalOrder` (duplicava o `applyTerminalFallback` do engine);
  - campo/param `pendingWithoutExchangeOrderIdTtlMs` (vinha de `PortfolioReservationTtlProperties.getTtlMs()`);
  - campo/param/import `ConciliationOrderUpdateExecutor conciliationOrderUpdate` (só o Step 3 usava);
  - bucket `zombies` em `RecoveryContext` e `zombiesCount` em `RecoverySummary`.

### 3. Boot — remoção do phase2 `reservation_ttl` (segunda expiração-cega)

Achado no review (Codex P1): o phase3 não era a única expiração-cega do boot. O `PortfolioReservationTtlUseCase` (phase2) rodava **antes** do phase3 e expirava `PENDING` sem `exchangeOrderId` por TTL **sem consultar a exchange** — o mesmo `buildSyntheticExpired` duplicado. Com defaults de produção (`reservation-ttl.enabled: true`, `ttl-ms: 300000`), uma ordem viva com ACK perdido era expirada antes do query-before-expire do phase3.

Como o phase3 agora **query-verifica todo `PENDING` inflight** no boot e o watchdog cobre runtime, a fase ficou redundante **e** nociva → **removida por inteiro**:

- Core: deletados `PortfolioReservationTtlUseCase`, `PortfolioReservationTtlResult`, `PortfolioReservationTtlStatus`; removidos a fase `phase2.reservation_ttl` e o método `runPhase2ReservationTtl` de `RunBootSequenceUseCase`; removidos os campos `ttlEnabled`/`ttlMs` de `BootExecutionCommand`.
- Spring: removidos o bean `portfolioReservationTtlUseCase` (`BootConfig`), o campo `portfolioReservationTtlProperties` + os args de TTL no `BootOrchestrator`; deletada `PortfolioReservationTtlProperties`; removido o bloco `reservation-ttl` do `application.yml`.

---

## Comportamento resultante

| Origem | Caminho (boot **e** runtime) | Exchange conhece? | Desfecho | DLQ? |
|---|---|---|---|---|
| `PENDING` (zombie) | query por `clientOrderId` | **sim** (ACK perdido, viva) | reconcilia `PENDING→SUBMITTED/FILLED`; capital **não** liberado | não |
| `PENDING` (zombie) | query por `clientOrderId` | **não** (nunca enviada) | `EXPIRED` via terminal fallback; capital liberado | **não** |
| `SUBMITTED`/`PARTIAL` | query por `clientOrderId` | não | runtime: **DLQ** · boot: terminal fallback | runtime sim |

Boot e runtime compartilham o **mesmo motor** (`RecoverTransactionStatusUseCase`); diferem só na política de not-found (boot sempre terminal-fallback; runtime DLQ para ordem confirmada).

---

## Arquivos alterados

| Arquivo | Mudança |
|---|---|
| `core/.../dto/runner/request/RecoverTransactionStatusRequest.java` | `forRuntimeWatchdog` → `DERIVE_FROM_STATUS`; novo valor de enum `DERIVE_FROM_STATUS` |
| `core/.../usecase/runner/RecoverTransactionStatusUseCase.java` | not-found resolve `DERIVE_FROM_STATUS` pelo `statusBefore` recarregado |
| `core/.../usecase/runner/RecoverStaleTransactionsUseCase.java` | `PENDING` elegível; request único (sem branching de status no lote) |
| `core/.../usecase/runner/RunnerBootRecoveryUseCase.java` | Remoção do Step 3 e da trilha de zombie; `PENDING` reconciliado junto do limbo; remoção de código morto |
| `core/.../dto/runner/RecoveryContext.java` | Removido bucket `zombies` |
| `core/.../usecase/boot/RunBootSequenceUseCase.java` | Log sem `zombies={}`; **removida a fase `phase2.reservation_ttl`** |
| `core/.../dto/boot/BootExecutionCommand.java` | Removidos `ttlEnabled`/`ttlMs` |
| `core/.../usecase/boot/phase2/PortfolioReservationTtlUseCase.java` (+Result/+Status) | **Deletados** |
| `spring-application/.../config/core/RunnerConfig.java` | Boot bean sem `reservationTtlProperties` nem `conciliationOrderUpdateExecutor` |
| `spring-application/.../config/core/BootConfig.java` | Removido bean `portfolioReservationTtlUseCase` |
| `spring-application/.../bootstrap/BootOrchestrator.java` | Removido `portfolioReservationTtlProperties` + args de TTL |
| `spring-application/.../bootstrap/PortfolioReservationTtlProperties.java` | **Deletada** |
| `spring-application/src/main/resources/application.yml` | Removido bloco `reservation-ttl` |

### Testes

| Arquivo | Mudança |
|---|---|
| `RecoverTransactionStatusUseCaseTest` | + `executeMarksPendingTransactionExpiredWhenExchangeDoesNotFindOrderInRuntimeMode` (cobre `DERIVE_FROM_STATUS` para `PENDING`) |
| `RunnerTransactionRecoveryWatchdogIntegrationTest` | + `watchdogShouldExpirePendingZombieNotFoundOnExchangeWithoutDlq`, + `watchdogShouldReconcilePendingZombieFoundAliveOnExchange` |
| `RunnerBootRecoveryIntegrationTest` | `recoveryShouldExpireZombie...` → `recoveryShouldQueryVerifyAndExpirePendingBuyNotFoundOnExchange`; + `recoveryShouldReconcilePendingBuyFoundAliveOnExchangeAndKeepReserve`; removido o teste de TTL-grace; removidos asserts de `zombiesCount` e o registro da property `reservation-ttl` |
| `BootOrchestrator{Observability,DlqOperational,BootMinimum}Test` | Removido o mock `PortfolioReservationTtlUseCase` e a property `ttlProperties` da composição |

Validação: `:core:test` e `:spring-application:test` (suíte completa) — todos verdes.

---

## Fora de escopo / Adiado

- **Observabilidade dedicada do zombie** (contador `reconciled_live` vs `expired_orphan`, alerta ativo do caso "viva", gancho com T21/T25): **não implementada nesta entrega**. Hoje os desfechos aparecem nos contadores genéricos do recovery (`recovered`/`routedToDlq`) e nos logs. Fica como follow-up.
- **Cancelar ordem viva por idade/TTL global** — anula estratégia; é da **T32** (opt-in por runner, default off).
- **Verbo `OrderDispatchPort.cancel`** — **T33**.
- **Carência de TTL para `PENDING` recém-criado** — removida; runtime/boot consultam a exchange antes de expirar (mais correto que a expiração cega). O cutoff do watchdog continua dando folga para ACK atrasado antes da seleção.

---

## Riscos / pontos de atenção

- **Política por status é invariante de segurança:** `PENDING`→terminal fallback, `SUBMITTED`/`PARTIAL`→DLQ no runtime. Inverter geraria falso conflito (zombie nunca-enviado na DLQ) ou perda de sinal (ordem confirmada sumindo sem DLQ).
- **Boot passou a exigir capacidade de order query também quando só há `PENDING`** (antes o zombie expirava local sem query). Em exchange sem order query, `PENDING` inflight leva ao mesmo halt que o limbo — consistente com "não expirar sem verificar".
- **Corrida query × fill tardio:** decidida pela conciliação idempotente (`ConciliationOrderUpdateExecutor`), nunca fora dela.
- **Liberação de capital:** sempre via conciliação, nunca direta.

---

## Critérios de aceitação

1. ✅ Um `PENDING` (zombie) velho é **consultado por `clientOrderId`** antes de expirar, em runtime, pelo mesmo recovery (sem use case paralelo).
2. ✅ `PENDING` viva (caso 3) é **reconciliada** (`SUBMITTED`); capital **não** liberado.
3. ✅ `PENDING` não encontrada vira **`EXPIRED`** via conciliação — **sem** DLQ.
4. ✅ `SUBMITTED`/`PARTIAL` not-found continua indo para **DLQ** no runtime (sem regressão).
5. ✅ Não existe `RuntimeZombieCleanupUseCase`/watchdog/properties dedicados; cobertura de zombie é extensão do recovery.
6. ✅ Boot não tem mais **nenhuma** expiração-cega: phase3 sem Step 3 **e** phase2 `reservation_ttl` removido; `PENDING` tratado só por query-before-expire (phase3) + watchdog de runtime.
7. ✅ A política de not-found do runtime é resolvida no engine pelo status **recarregado** (`DERIVE_FROM_STATUS`), sem corrida com a seleção do lote.
8. ⏳ **Adiado** — métrica distinguindo `reconciled_live` de `expired_orphan` com alerta ativo.
9. ✅ Boot, recovery (`SUBMITTED`/`PARTIAL`) e reconciliação existentes seguem sem regressão (suíte completa verde).
