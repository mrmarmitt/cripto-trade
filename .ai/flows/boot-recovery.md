# Boot Recovery

## Objetivo

Executar a sequencia de boot operacional, validar dependencias externas,
identificar inconsistencias entre estado local e exchange, aplicar recuperacoes
seguras e expor observabilidade do processo.

## Quando Consultar

- Mudancas na inicializacao da aplicacao.
- Bugs em readiness de exchange.
- Divergencias de saldo, ordens abertas, zombies ou transacoes antigas.
- Alteracoes em `FAIL_FAST` ou `WARN_ONLY`.
- Recuperacao de runners e DLQ operacional.
- Limpeza de transacoes `PENDING` antigas (a expiracao por TTL nao e mais uma fase
  do boot desde a T31; vive em runtime no `RecoverStaleTransactionsUseCase`).

## Entradas E Saidas

- Entrada principal: execucao de `RunBootSequenceUseCase`.
- Escopo: portfolios e runners elegiveis.
- Saida principal: boot concluido ou falha observada.
- Efeitos colaterais: conciliacoes sinteticas, amostras de zombie em DLQ,
  eventos de observabilidade e recuperacao de transacoes.

## Fluxo Principal

1. Cria `runId` de boot.
2. Carrega portfolios.
3. Carrega runners elegiveis, excluindo estados como `ARCHIVED` e
   `TERMINATING`.
4. Executa Phase 1 de readiness por exchange.
5. Executa Phase 2 de conferencia de saldo por exchange (T57).
6. Executa Phase 2 de zombie detection.
7. Executa Phase 3 de runner recovery.
8. Notifica conclusao ou falha pelo observer.

## Phase 1: Readiness

`ExchangeBootReadinessPort.checkBootReadiness` verifica se as exchanges
necessarias estao prontas para operacao.

Exchange nao pronta bloqueia o boot em modo fail-fast.

## Phase 2: Conferencia de saldo por exchange (T57)

`ExchangeBalanceReconciliation` (`core/.../usecase/exchange`) compara, por exchange, a **soma** dos
portfolios que tem runner nao arquivado nela com a conta (`free + locked`), quando o adapter oferece
consulta de conta. O portfolio e orcamento virtual sobre conta compartilhada:

- moeda de cotacao: `available + reserved - custo dos lotes abertos` (o custo da BUY executada fica em
  `reserved` ate a SELL casar, mas na conta ja virou o ativo);
- ativo comprado: soma da quantidade dos lotes `OPEN`/`CLOSING`.

Status (`ExchangeBalanceStatus`, em ordem de severidade):

- `OK` — conta cobre o esperado; o excedente e `unallocated`
- `PENDING` — falta coberta pelas ordens em voo (fill durante a parada; a Phase 3 concilia)
- `SKIPPED` — exchange sem consulta de conta
- `FAILED` — consulta falhou ou saldo local ausente
- `DEFICIT` — falta alem das ordens em voo: HALT automatico (`BALANCE_DIVERGENCE`) de **todos** os
  portfolios da exchange

`DEFICIT` e `FAILED` falham o boot em `FAIL_FAST`. A mesma unidade alimenta
`EXCHANGE_BALANCE_DIVERGENCE` (somente leitura) e a avaliacao periodica
(`EvaluateExchangeBalanceUseCase`, a cada 5 min, HALT na 2a leitura seguida em `DEFICIT`).

## Phase 2: Zombie Detection

`PortfolioZombieDetectionUseCase` lista ordens abertas na exchange e tenta
correlacionar com runners/transacoes locais.

Classificacoes relevantes:

- `clientOrderId` invalido.
- runner desconhecido.
- ordem sem transacao local.
- ordem antes do cutoff.
- simbolo fora do escopo.

Em `FAIL_FAST`, zombies detectados podem bloquear boot e persistir amostras em
DLQ operacional. Em `WARN_ONLY`, deteccoes sao observadas sem bloquear.

## Expiracao de PENDING antigas (não é mais fase de boot — T31)

A antiga "Phase 2: Reservation TTL" (`PortfolioReservationTtlUseCase`, expiracao
local cega de `PENDING` por cutoff) **foi removida na T31** e nao faz parte da
sequencia de boot. Duas coberturas a substituem:

- **No boot:** um `PENDING` sem `exchangeOrderId` agora e *consultado na exchange
  antes de expirar* (query-before-expire), pelo mesmo `RecoverTransactionStatusUseCase`
  usado para o limbo em Phase 3 — so vira terminal local se a exchange nao conhecer
  a ordem.
- **Em runtime:** a limpeza de `PENDING`/zombie antigas vive no
  `RecoverStaleTransactionsUseCase` (watchdog), tambem com query-before-expire.

## Phase 3: Runner Recovery

`RunnerBootRecoveryUseCase` recupera cada runner elegivel. O fluxo observa
transacoes em voo, zombies, limbo e outros sinais operacionais, sempre usando os
contratos centrais de conciliacao quando precisa alterar estado local.

O mesmo `recoverRunner` e usado fora do boot pelo `ManageRunnerLifecycleUseCase` (T49): o start de
um runner CREATED e o resume de um runner HALTED reconciliam por ele; o resumo traz `completed` e o
resume so reativa com reconciliacao limpa.

## Observabilidade

`BootExecutionObserverPort` recebe eventos de fase, resultados, falhas fail-fast
e conclusao do run. Mudancas no boot devem preservar esses pontos de observacao,
porque eles sao usados para diagnostico operacional e testes.

Historico (T59): ao terminar, com sucesso ou falha, o `RunBootSequenceUseCase` grava o boot em
`boot_runs` (`BootRunRepositoryPort`: modo, inicio, fim, fase e mensagem da falha). `/api/boot/status`
continua mostrando so o boot corrente, em memoria; o historico aparece em `GET /api/admin/events`
(`BOOT_COMPLETED`/`BOOT_FAILED`). Falha ao gravar so loga: boot sem banco nao tem onde registrar.
As transicoes que a reconciliacao faz no runner levam a causa de quem a pediu: `BOOT` no boot,
`MANUAL` (com o `X-Reason`) no start/resume do operador.

Logs sentinel relevantes (T23 G3/G4):

- `bootSequence.phase2.sanity: completed runId={} result={} exchanges={}` — `result`
  e o pior status agregado da fase (`OK/PENDING/SKIPPED/FAILED/DEFICIT` ou `NONE`).
- `bootSequence.phase2.zombie: completed runId={} detected={} mode={}` — `detected` e a
  contagem de zombies; `mode` distingue `WARN_ONLY` de `FAIL_FAST`.
- `bootRecovery: runner halted runnerId={} reason=DLQ_PENDING portfolioId={}` — emitido
  **apenas** quando `RunnerBootRecoveryUseCase` de fato executa `ACTIVE→HALTED` por DLQ
  pendente (alerta `#alerts-error`). Nao dispara para runners nao-ACTIVE nem para halts
  por outros erros de reconciliacao.

Catalogo completo de indicadores/alertas em `/.ai/monitoring-spec.md`.

**Rastreamento end-to-end (T26):** o boot recovery **nao** gerencia MDC de `transactionId` (que
vive so na `ConciliationOrderUpdate`). Os logs de recovery do `step4ReconcileLimbo`
(`bootRecovery: ... transactionId=...`) carregam o id no texto da mensagem, recuperaveis por
`{app="ctrade"} |= "transactionId=X"`; a conciliacao acionada por baixo emite seus proprios logs
com o `transactionId` no MDC (campo JSON).

## Componentes Principais

| Componente | Papel |
| --- | --- |
| `core/src/main/java/com/marmitt/core/application/usecase/boot/RunBootSequenceUseCase.java` | Orquestra as fases de boot. |
| `core/src/main/java/com/marmitt/core/application/usecase/boot/phase2/PortfolioBootSanityUseCase.java` | Compara saldos locais e externos. |
| `core/src/main/java/com/marmitt/core/application/usecase/boot/phase2/PortfolioZombieDetectionUseCase.java` | Detecta ordens abertas nao conciliadas. |
| `core/src/main/java/com/marmitt/core/application/usecase/runner/RunnerBootRecoveryUseCase.java` | Recupera runners individualmente (query-before-expire para PENDING). |
| `core/src/main/java/com/marmitt/core/application/usecase/runner/RecoverStaleTransactionsUseCase.java` | Watchdog de runtime: expira PENDING/zombie antigas com query-before-expire (T31). |
| `core/src/main/java/com/marmitt/core/application/usecase/runner/orderconciliation/ConciliationOrderUpdateExecutor.java` | Aplica recuperacoes pela mesma regra da conciliacao normal. |
| `spring-application/src/main/java/com/marmitt/application/spring/config/core/RunnerConfig.java` | Wires do boot, executores e propriedades. |

## Invariantes

- `WARN_ONLY` nao deve bloquear boot apenas por zombie detectado.
- `FAIL_FAST` deve registrar a fase real que falhou.
- Recuperacoes que mudam transacao devem usar o executor central de conciliacao.
- Transacao `PENDING` sem `exchangeOrderId` so pode virar terminal local depois de
  consultada na exchange (query-before-expire); nunca por expiracao local cega.
- Runners arquivados ou em terminacao nao entram no escopo operacional normal.

## Validacao

- `spring-application/src/test/java/com/marmitt/application/spring/bootstrap/BootOrchestratorBootMinimumTest.java`
- `spring-application/src/test/java/com/marmitt/application/spring/bootstrap/BootOrchestratorDlqOperationalTest.java`
- `spring-application/src/test/java/com/marmitt/application/spring/bootstrap/BootOrchestratorObservabilityTest.java`
- `spring-application/src/test/java/com/marmitt/application/spring/bootstrap/RunnerBootRecoveryIntegrationTest.java`
- `spring-application/src/test/java/com/marmitt/application/spring/bootstrap/RunnerTransactionRecoveryWatchdogIntegrationTest.java`
- `spring-application/src/test/java/com/marmitt/application/spring/config/exchange/BinanceBootReadinessIntegrationTest.java`
- Comando recomendado:
  `./scripts/gradle-run.ps1 -q :core:test :spring-application:test`

## Fontes

- `docs/IMPLEMENTATION_GUIDE.md`, secao 10.
- `docs/PHASE0_INVARIANTS.md`.
- `core/src/main/java/com/marmitt/core/application/usecase/boot/`.
- `core/src/main/java/com/marmitt/core/application/usecase/runner/RunnerBootRecoveryUseCase.java`.
- `spring-application/src/main/java/com/marmitt/application/spring/config/core/RunnerConfig.java`.
