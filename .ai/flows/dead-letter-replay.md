# Dead Letter E Replay

## Objetivo

Mapear como o projeto registra entradas de DLQ, como elas bloqueiam fluxos
operacionais e quando podem ser resolvidas ou reprocessadas automaticamente.

Use este mapa para bugs ou features envolvendo:

- callbacks ou ordens que nao podem ser reconciliados;
- retries esgotados em eventos de capital;
- boot/recovery que encontra ordens zumbis ou limbo irreconciliavel;
- telas, endpoints ou jobs de resolucao/replay de DLQ.

## Entradas E Saidas

Entradas principais:

- eventos de capital publicados apos commit do runner;
- ordens abertas consultadas durante boot phase2 zombie detection;
- transacoes em voo consultadas por boot recovery ou watchdog;
- chamadas REST para listar, resolver ou reprocessar DLQ.

Saidas e efeitos colaterais:

- `DeadLetterEntry` persistida em `dead_letter_entries`;
- entrada marcada como resolvida quando operador resolve manualmente;
- entrada marcada como resolvida apos replay automatico aplicado;
- runner pode ficar `HALTED` se ainda existir DLQ nao resolvida na finalizacao
  do recovery.

## Fluxo Principal

1. O core cria `DeadLetterEntry` com `portfolioId`, `runnerId` opcional,
   `clientOrderId`, `exchangeOrderId`, `rawPayload`, `DlqReason` e estado
   `isResolved=false`.
2. A persistencia passa por `DeadLetterEntryRepositoryPort`.
3. A implementacao JDBC salva em `dead_letter_entries` e consulta pendencias por
   portfolio, runner ou identidade.
4. A operacao manual entra por `DeadLetterController`.
5. `TransactionalManageDeadLetterPort` aplica transacao:
   - listagem e read-only;
   - resolve/reprocess em `REQUIRES_NEW`.
6. `ManageDeadLetterUseCase` valida a entrada:
   - nao encontrada retorna falha;
   - ja resolvida retorna conflito;
   - resolve manual marca `resolvedBy` e `resolvedAt`;
   - replay automatico exige `DeadLetterReprocessingPort.supports(entry)`.
7. Se o reprocessor retorna `applied=true`, a entrada e resolvida. Se retorna
   `applied=false`, a DLQ permanece aberta para revisao manual.

## Tipos De DLQ

### Capital

`CapitalEventListener` consome `ExecutionConfirmedEvent` e
`MarginReleaseEvent` com `@TransactionalEventListener(AFTER_COMMIT)`,
`REQUIRES_NEW` e retry Spring.

Quando os retries acabam, os metodos `@Recover` serializam o evento com
`CapitalDeadLetterPayloadCodec` e persistem uma DLQ com
`DlqReason.RETRY_EXHAUSTED`.

Replay automatico:

- `CapitalDeadLetterReprocessingAdapter.supports` aceita apenas
  `RETRY_EXHAUSTED`;
- o payload precisa decodificar como `EXECUTION_CONFIRMED` ou
  `MARGIN_RELEASE`;
- `EXECUTION_CONFIRMED` chama `ExecutionConfirmedReaction`;
- `MARGIN_RELEASE` chama `MarginReleasedReaction` somente se o
  `GlobalBalance.reservedBalance` ainda cobre o valor a liberar;
- se margem ja foi consumida/liberada e nao ha mudanca segura de estado, o replay
  retorna `notApplied` e exige revisao manual.

### Boot Zombie Detection

`PortfolioZombieDetectionUseCase` classifica ordens abertas da exchange em:

- `INVALID_FORMAT`;
- `UNKNOWN_RUNNER`;
- `RECONCILIATION_CONFLICT`;
- `UNKNOWN_SYMBOL`.

`RunBootSequenceUseCase` persiste amostras em DLQ apenas quando a phase2 zombie
detection roda em `FAIL_FAST`. Em `WARN_ONLY`, o fluxo observa e loga, mas nao
persiste a DLQ operacional.

Antes de persistir, o boot consulta `existsUnresolvedByIdentity` para evitar
duplicar entradas abertas com a mesma identidade.

### Runtime Recovery

`RecoverTransactionStatusUseCase` pode rotear uma transacao para DLQ quando a
exchange nao encontra uma ordem e a policy escolhida e `REGISTER_DLQ`.

O payload e textual e identifica:

- `source=runner.recovery.transaction`;
- exchange;
- runner;
- transaction;
- client/exchange order ids;
- status anterior;
- outcome `ORDER_NOT_FOUND_ON_EXCHANGE`.

Se ja existe DLQ aberta para a mesma identidade, o fluxo mantem a entrada
existente.

### Runner Boot Recovery

`RunnerBootRecoveryUseCase` consulta DLQ nao resolvida na finalizacao:

- `existsUnresolvedByRunnerId(runnerId)`;
- `existsUnresolvedByPortfolioIdAndRunnerIsNull(portfolioId)`.

Se houver pendencia, o recovery registra erro. Runner `ACTIVE` e movido para
`HALTED`; caso contrario a reconciliacao nao e marcada como concluida.

## Componentes Principais

- `core/src/main/java/com/marmitt/core/domain/portfolio/DeadLetterEntry.java`
- `core/src/main/java/com/marmitt/core/enums/DlqReason.java`
- `core/src/main/java/com/marmitt/core/application/usecase/portfolio/ManageDeadLetterUseCase.java`
- `core/src/main/java/com/marmitt/core/ports/outbound/repository/DeadLetterEntryRepositoryPort.java`
- `core/src/main/java/com/marmitt/core/ports/outbound/repository/DeadLetterReprocessingPort.java`
- `spring-application/src/main/java/com/marmitt/application/spring/controller/DeadLetterController.java`
- `spring-application/src/main/java/com/marmitt/application/spring/service/TransactionalManageDeadLetterPort.java`
- `spring-application/src/main/java/com/marmitt/application/spring/handler/CapitalEventListener.java`
- `spring-application/src/main/java/com/marmitt/application/spring/deadletter/CapitalDeadLetterPayloadCodec.java`
- `spring-application/src/main/java/com/marmitt/application/spring/deadletter/CapitalDeadLetterReprocessingAdapter.java`
- `spring-application/src/main/java/com/marmitt/application/spring/infrastructure/persistence/adapter/JdbcDeadLetterEntryRepositoryAdapter.java`
- `core/src/main/java/com/marmitt/core/application/usecase/boot/RunBootSequenceUseCase.java`
- `core/src/main/java/com/marmitt/core/application/usecase/runner/RunnerBootRecoveryUseCase.java`
- `core/src/main/java/com/marmitt/core/application/usecase/runner/RecoverTransactionStatusUseCase.java`

## Invariantes

- DLQ aberta nao deve ser duplicada quando a identidade ja existe aberta.
- Replay automatico so resolve a entrada depois de efeito aplicado.
- Payload de capital e detalhe de adapter Spring; o core conhece apenas
  `DeadLetterEntry`, `DlqReason` e portas.
- `runnerId` pode ser nulo para DLQ de portfolio sem escopo de runner.
- Resolucao manual exige operador (`resolvedBy`/`requestedBy`) nao vazio.
- Entrada ja resolvida nao pode ser resolvida nem reprocessada novamente.
- DLQ operacional aberta impede finalizacao limpa do runner recovery.

## Validacao Recomendada

- `./scripts/gradle-run.ps1 -q :core:test --tests "*ManageDeadLetterUseCaseTest"`
- `./scripts/gradle-run.ps1 -q :spring-application:test --tests "*DeadLetterControllerTest"`
- `./scripts/gradle-run.ps1 -q :spring-application:test --tests "*CapitalDeadLetterReprocessingAdapterTest"`
- `./scripts/gradle-run.ps1 -q :spring-application:test --tests "*CapitalDeadLetterReplayIntegrationTest"`
- `./scripts/gradle-run.ps1 -q :spring-application:test --tests "*BootOrchestratorDlqOperationalTest"`
- `./scripts/gradle-run.ps1 -q :spring-application:test --tests "*RunnerTransactionRecoveryWatchdogIntegrationTest"`

## Fontes

- Codigo listado em "Componentes Principais".
- Migrations `V1__create_initial_schema.sql` e
  `V8__add_runner_id_to_dead_letter_entries.sql`.
- Testes de use case, controller, replay de capital, boot DLQ e watchdog.
