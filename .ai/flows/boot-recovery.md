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
- Recuperacao de runners, TTL de reserva e DLQ operacional.

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
5. Executa Phase 2 de sanity check por portfolio.
6. Executa Phase 2 de zombie detection.
7. Executa Phase 2 de reservation TTL.
8. Executa Phase 3 de runner recovery.
9. Notifica conclusao ou falha pelo observer.

## Phase 1: Readiness

`ExchangeBootReadinessPort.checkBootReadiness` verifica se as exchanges
necessarias estao prontas para operacao.

Exchange nao pronta bloqueia o boot em modo fail-fast.

## Phase 2: Portfolio Sanity

`PortfolioBootSanityUseCase` compara o estado local do `GlobalBalance` com a
conta externa quando o adapter oferece consulta de conta.

Resultados possiveis incluem:

- `PASS`
- `WARN_SURPLUS`
- `FAIL_DEFICIT`
- `SKIPPED`
- `FAILED`

Resultados criticos falham o boot em `FAIL_FAST`.

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

## Phase 2: Reservation TTL

`PortfolioReservationTtlUseCase` procura transacoes `PENDING` antigas sem
`exchangeOrderId`.

Quando uma transacao passa do cutoff, o fluxo cria um `OrderDataDto` sintetico
com status `EXPIRED` e reaproveita `ConciliationOrderUpdateExecutor`. Assim, a
transicao segue a mesma regra transacional da conciliacao normal.

## Phase 3: Runner Recovery

`RunnerBootRecoveryUseCase` recupera cada runner elegivel. O fluxo observa
transacoes em voo, zombies, limbo e outros sinais operacionais, sempre usando os
contratos centrais de conciliacao quando precisa alterar estado local.

## Observabilidade

`BootExecutionObserverPort` recebe eventos de fase, resultados, falhas fail-fast
e conclusao do run. Mudancas no boot devem preservar esses pontos de observacao,
porque eles sao usados para diagnostico operacional e testes.

## Componentes Principais

| Componente | Papel |
| --- | --- |
| `core/src/main/java/com/marmitt/core/application/usecase/boot/RunBootSequenceUseCase.java` | Orquestra as fases de boot. |
| `core/src/main/java/com/marmitt/core/application/usecase/boot/phase2/PortfolioBootSanityUseCase.java` | Compara saldos locais e externos. |
| `core/src/main/java/com/marmitt/core/application/usecase/boot/phase2/PortfolioZombieDetectionUseCase.java` | Detecta ordens abertas nao conciliadas. |
| `core/src/main/java/com/marmitt/core/application/usecase/boot/phase2/PortfolioReservationTtlUseCase.java` | Expira reservas antigas sem ordem externa. |
| `core/src/main/java/com/marmitt/core/application/usecase/runner/RunnerBootRecoveryUseCase.java` | Recupera runners individualmente. |
| `core/src/main/java/com/marmitt/core/application/usecase/runner/orderconciliation/ConciliationOrderUpdateExecutor.java` | Aplica recuperacoes pela mesma regra da conciliacao normal. |
| `spring-application/src/main/java/com/marmitt/application/spring/config/core/RunnerConfig.java` | Wires do boot, executores e propriedades. |

## Invariantes

- `WARN_ONLY` nao deve bloquear boot apenas por zombie detectado.
- `FAIL_FAST` deve registrar a fase real que falhou.
- Recuperacoes que mudam transacao devem usar o executor central de conciliacao.
- Transacao `PENDING` antiga sem `exchangeOrderId` pode expirar por TTL; se ja
  tem `exchangeOrderId`, nao deve ser expirada por esse caminho.
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
