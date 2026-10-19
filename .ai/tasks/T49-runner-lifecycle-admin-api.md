# T49 — Ciclo de Vida do Runner por API (start, resume, terminate)

**Complexidade:** Média/Alta
**Responsável:** Claude
**Dependências:** nenhuma. **Crítica para operar:** sem ela, um runner parado não volta.
**Status:** Concluído (2026-10-16) — ver "Decisões registradas" e "Entrega".

---

## Descrição

Achado da T39 (IG §13.1, §13.3, §13.4, §13.6, §12.5, §7.4.4):

- `StrategyRunner.resume()`, `startTerminating()` e `archive()` existem e **não têm chamador**.
- Runner criado por `POST /api/portfolios/{id}/runners` fica `CREATED` e só vira `ACTIVE` no próximo
  boot (`RunnerBootRecoveryUseCase`, passo 6).
- Runner parado pelo kill switch (T8) ou pelo boot (`ACTIVE → HALTED`) **nunca volta** a `ACTIVE` sem
  editar o banco: no boot ele só conclui a reconciliação e continua `HALTED`.
- Não há como encerrar um runner com posição aberta, nem alterar limites mutáveis em runtime.

## Solução proposta

1. Use cases no core (um por transição, regras no domínio do `StrategyRunner`):
   - **start** (`CREATED → INITIALIZING → ACTIVE`) reaproveitando a reconciliação do boot para aquele
     runner (mesmo caminho do `RunnerBootRecoveryUseCase`, sem duplicar a lógica);
   - **resume** (`HALTED → ACTIVE`) exigindo DLQ do runner/portfolio resolvida e reconciliação limpa;
   - **terminate** (`ACTIVE/HALTED → TERMINATING`): bloqueia BUY, permite SELL de fechamento; worker
     move para `ARCHIVED` quando não houver posição, ordem em voo nem reserva (IG §13.4);
   - **update limits** (`maxAllocationPercent`, `maxOpenPositions`, `maxPendingOrders`) — campos
     mutáveis de IG §13.2.2.
2. Endpoints admin (API key da T8): `POST /api/admin/runners/{id}/start|resume|terminate`,
   `PATCH /api/admin/runners/{id}`.
3. Health check de ativação (IG §13.3): exchange alcançável e símbolo com filtros carregados.
4. Comandos por posição/ordem (IG §12.5): `FORCE_CANCEL` de uma ordem e `FORCE_CLOSE_POSITION`
   reaproveitando o pipeline de cancel (`buildCancelCommand`) e a liquidação MARKET da T45.

## Decisões registradas (2026-10-16)

- **Encerramento com posição aberta (mantenedor):** o runner para de comprar e espera. Em TERMINATING
  a estratégia continua e pode vender; o runner é arquivado sozinho quando não houver posição nem
  ordem em voo. Para não esperar, o operador fecha a posição a mercado pelo comando.
- **Dois predicados no runner (implementação):** `canAcceptSignals` = ACTIVE ou TERMINATING fora de
  reconciliação (conduzido pelo pipeline — estratégia ou Safe Mode); `canOpenPositions` = só ACTIVE
  (BUY). Todas as releituras do runner no fluxo de sinal passam por um único `requireRunner(id, condição)`.
- **start/resume reconciliam pelo caminho do boot** (`RunnerBootRecoveryUseCase.recoverRunner`, agora
  com `completed` no resumo). O start ativa no passo final; o resume só volta a ACTIVE se a
  reconciliação terminar limpa (DLQ resolvida). Start interrompido deixa o runner em INITIALIZING e
  pode ser repetido (`startInitializing` tolera INITIALIZING).
- **Health check (IG §13.3):** adapter registrado e envio de ordens liberado (`isDispatchBlocked`).
  Filtros de símbolo ficam com o normalizador/adapter.
- **Kill switch vale para TERMINATING:** `halt()` aceita ACTIVE ou TERMINATING; ao retomar o runner
  volta a ACTIVE e o encerramento precisa ser pedido de novo.
- **Terminate a partir de HALTED** coloca o runner de volta no pipeline só para vender (IG §13.1).
- **Comandos reaproveitam caminhos existentes:** cancelar ordem usa o mesmo `CancelSignalHandler` do
  `SHOULD_CANCEL` (agora com desfecho `CancelOutcome`); fechar posição usa o sinal de liquidação MARKET
  da T45 (`SafeModeHandler.liquidationSignal`) pelo pipeline de SELL, com o último preço (T43) como
  referência. Fechar posição de runner halted é recusado: retome antes (kill switch = nenhuma ordem).
- **`RunnerHaltResult` virou `RunnerTransitionResult`**, resultado único de todas as transições.
- **Código morto removido:** `RunnerStatus.isOperational()`/`isTerminal()` (sem chamador, Javadoc divergente).

## Entrega (2026-10-16)

| Camada | Mudança |
|---|---|
| core (domínio) | `StrategyRunner.canOpenPositions`, `updateLimits`, `halt` a partir de TERMINATING; transições inválidas lançam `RunnerOperationException` (subclasse de `IllegalStateException`) |
| core (use cases) | `ManageRunnerLifecyclePort`/`ManageRunnerLifecycleUseCase` (start, resume, terminate, archiveCompleted, updateLimits); `RunnerOrderCommandPort` no `ProcessTradeSignalUseCase` (cancelOrder, closePosition); `HaltRunnerUseCase` inclui TERMINATING |
| spring | `POST /api/admin/runners/{id}/start\|resume\|terminate`, `PATCH /api/admin/runners/{id}`, `POST /api/admin/runners/{id}/orders/{tx}/cancel`, `POST /api/admin/runners/{id}/positions/{pos}/close`; `RunnerArchiveScheduler` (`runner.lifecycle.termination-check-interval-ms`); consultas operacionais incluem TERMINATING; 409 para `RunnerOperationException`/`ConcurrentPositionLockException` |

### Validação

- `:core:test` 190 — `ManageRunnerLifecycleUseCaseTest`, `StrategyRunnerInvariantsTest` (TERMINATING
  vende sem abrir posição, halt de TERMINATING, transições inválidas, start retomável, limites),
  `CancelSignalHandlerTest` (desfechos).
- `:spring-application:test` 197 — `RunnerLifecycleE2ETest` (MOCK): runner criado inicia sem restart;
  halt → resume volta a avaliar sinais; cancelamento pelo operador devolve a reserva; runner em
  TERMINATING tem o lote fechado a mercado pelo comando e é arquivado. `AdminRunnerControllerTest`.

### Riscos residuais

- **start/resume síncronos:** a reconciliação consulta a exchange dentro da requisição HTTP (timeouts
  e tentativas do boot, `runner.boot.phase3.*`).
- **Estratégia que nunca vende** deixa o runner em TERMINATING até o operador fechar a posição.
- **Limites `maxOpenPositions`/`maxPendingOrders`** já são alteráveis, mas só passam a valer com a T50.

## Decisões pendentes

- Nenhuma.

## Critérios de aceitação

1. Runner criado pela API pode ser iniciado sem restart.
2. Runner halted pelo kill switch volta a operar por API, com validação.
3. Encerramento leva a `ARCHIVED` só depois de zerar posição, ordens e reserva.
4. Limites mutáveis alterados em runtime valem na próxima reserva.
