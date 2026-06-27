# Portfolio Capital

## Objetivo

Controlar capital por portfolio, incluindo saldo disponivel, saldo reservado,
resultado realizado, reserva para BUY, confirmacao financeira de execucoes e
liberacao de margem em encerramentos.

## Quando Consultar

- Mudancas em `GlobalBalance`.
- Bugs de reserva, liberacao, PnL ou taxa.
- Alteracoes em Safe Mode, limite de exposicao por runner ou capital pooling.
- Eventos financeiros disparados pela conciliacao de ordens.
- DLQ e replay de eventos de capital.

## Entradas E Saidas

- Entrada para reserva: runner, portfolio, valor solicitado e exposicao atual.
- Entrada para confirmacao: `ExecutionConfirmedEvent`.
- Entrada para liberacao: `MarginReleaseEvent`.
- Saida principal: `GlobalBalance` atualizado.
- Efeitos colaterais: registro idempotente de evento de capital e, em falha
  persistente, dead letter.

## Modelo Principal

`GlobalBalance` mantem:

- `availableBalance`
- `reservedBalance`
- `realizedBalance`
- `initialCapital`
- `baseCurrency`
- `totalFeesPaid`
- `lastExecutionTime`

`getTotalBalance` considera `availableBalance + reservedBalance`.

## Criacao De Portfolio

1. `CreatePortfolioUseCase` valida unicidade de nome.
2. Cria `Portfolio`.
3. Registra o portfolio.
4. Cria `GlobalBalance` inicial com capital e moeda base.

## Reserva Para BUY

`CapitalReservationPolicy` decide se um sinal BUY pode seguir:

1. Runner precisa estar associado a portfolio conhecido.
2. Safe Mode do portfolio nao pode bloquear operacao.
3. `GlobalBalance` precisa existir.
4. Exposicao atual + valor solicitado precisa respeitar
   `maxAllocationPercent` do runner.
5. Reserva atomica precisa mover capital de disponivel para reservado.

Falha em qualquer passo levanta `CapitalReservationRejectedException`. O
`BuySignalHandler` descarta o sinal e nao envia ordem externa.

## Confirmacao Financeira

`ExecutionConfirmedReaction` processa `ExecutionConfirmedEvent` apos match de
SELL:

1. Registra idempotencia por `matchId`.
2. Resolve runner e portfolio.
3. Carrega `GlobalBalance`.
4. Chama `confirmExecution(totalCost, pnlRealized, feeConverted)`.
5. Salva o saldo.

Esse metodo reduz reserva, aplica PnL realizado, devolve custo ao disponivel e
acumula taxa convertida.

## Liberacao De Margem

`MarginReleasedReaction` processa `MarginReleaseEvent`:

1. Registra idempotencia por `transactionId`.
2. Resolve runner e portfolio.
3. Carrega `GlobalBalance`.
4. Faz guarda defensiva contra liberacao maior que reservado.
5. Chama `release(amount)`.
6. Salva o saldo.

## Observabilidade

Quando a idempotencia rejeita um evento duplicado (`matchId`/`transactionId` ja
processado), ambas as reactions emitem log em nivel `info` (T23 G5):
`executionConfirmedReaction: duplicate ignored ...` e
`marginReleasedReaction: duplicate ignored ...`. Antes era `debug` (invisivel no
Loki), o que escondia replays inesperados de eventos de capital. Catalogo de
indicadores em `/.ai/monitoring-spec.md`.

**Rastreamento end-to-end (T26):** o `CapitalEventListener` ancora o `transactionId` no MDC
ao consumir `ExecutionConfirmedEvent`/`MarginReleaseEvent` — inclusive no caminho
`@Recover`/DLQ — de modo que todos os logs do processamento (e retries) carreguem o id e
sejam recuperaveis via `| json | transactionId="X"` no Loki.

## DLQ De Capital

`CapitalEventListener` consome eventos apos commit em uma transacao nova. Quando
as tentativas se esgotam, o payload e persistido em dead letter para investigacao
ou replay.

`ManageDeadLetterUseCase` lista pendencias, marca resolvidas e reprocessa via
`DeadLetterReprocessingPort`.

## Componentes Principais

| Componente | Papel |
| --- | --- |
| `core/src/main/java/com/marmitt/core/domain/portfolio/GlobalBalance.java` | Regras de saldo disponivel, reservado e realizado. |
| `core/src/main/java/com/marmitt/core/domain/portfolio/Portfolio.java` | Estado do portfolio e Safe Mode. |
| `core/src/main/java/com/marmitt/core/application/usecase/portfolio/CreatePortfolioUseCase.java` | Cria portfolio e saldo inicial. |
| `core/src/main/java/com/marmitt/core/application/usecase/runner/processsignal/CapitalReservationPolicy.java` | Reserva capital para BUY. |
| `core/src/main/java/com/marmitt/core/application/reaction/ExecutionConfirmedReaction.java` | Aplica efeito financeiro de execucao confirmada. |
| `core/src/main/java/com/marmitt/core/application/reaction/MarginReleasedReaction.java` | Libera margem de transacao encerrada. |
| `spring-application/src/main/java/com/marmitt/application/spring/handler/CapitalEventListener.java` | Consome eventos financeiros com retry e DLQ. |
| `core/src/main/java/com/marmitt/core/application/usecase/portfolio/ManageDeadLetterUseCase.java` | Operacao de DLQ. |

## Invariantes

- Saldos nao podem ficar negativos.
- BUY nao pode reservar o mesmo capital mais de uma vez.
- Liberacao de margem terminal nao pode duplicar efeito financeiro.
- Eventos de capital precisam ser idempotentes.
- Safe Mode deve impedir novas reservas quando ativo.
- Limite de exposicao do runner deve considerar exposicao atual mais reserva
  solicitada.

## Validacao

- `core/src/test/java/com/marmitt/core/domain/portfolio/GlobalBalanceInvariantsTest.java`
- `spring-application/src/test/java/com/marmitt/application/spring/handler/CapitalEventListenerTest.java`
- `spring-application/src/test/java/com/marmitt/application/spring/bootstrap/CapitalDeadLetterReplayIntegrationTest.java`
- `spring-application/src/test/java/com/marmitt/application/spring/deadletter/CapitalDeadLetterReprocessingAdapterTest.java`
- `spring-application/src/test/java/com/marmitt/application/spring/controller/DeadLetterControllerTest.java`
- `core/src/test/java/com/marmitt/core/application/usecase/portfolio/ManageDeadLetterUseCaseTest.java`
- Comando recomendado:
  `./scripts/gradle-run.ps1 -q :core:test :spring-application:test`

## Fontes

- `docs/IMPLEMENTATION_GUIDE.md`, secoes 7 e 10.
- `docs/PHASE0_INVARIANTS.md`.
- `core/src/main/java/com/marmitt/core/domain/portfolio/`.
- `core/src/main/java/com/marmitt/core/application/usecase/portfolio/`.
- `spring-application/src/main/java/com/marmitt/application/spring/handler/CapitalEventListener.java`.
