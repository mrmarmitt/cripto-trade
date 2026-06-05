# Order Conciliation

## Objetivo

Aplicar atualizacoes de ordens vindas da exchange ao estado local de
transacoes, posicoes, matches e eventos financeiros, preservando idempotencia e
transicoes validas.

O mesmo executor atende o fluxo normal de eventos e os fluxos de boot/recovery.

## Quando Consultar

- Bugs em `NEW`, `PARTIALLY_FILLED`, `FILLED`, `CANCELED`, `EXPIRED` ou
  `REJECTED`.
- Mudancas em idempotencia de fills.
- Alteracoes em abertura/reducao de posicao.
- Recuperacao de transacoes durante boot.
- Liberacao de margem em encerramentos sem fill final.

## Entradas E Saidas

- Entrada principal: `OrderDataDto`.
- Identificador: `clientOrderId`.
- Saida principal: `Transaction` atualizada.
- Efeitos colaterais: alteracao de `Position`, criacao de `TransactionMatch`,
  publicacao de `ExecutionConfirmedEvent` ou `MarginReleaseEvent`.

## Fluxo Principal

1. `OrderConciliationUseCase` recebe `OrderDataDto`.
2. `ConciliationOrderUpdateExecutor` abre a fronteira transacional apropriada.
3. `ConciliationOrderUpdate` valida `clientOrderId`.
4. Carrega a transacao local correspondente.
5. Roteia pelo status recebido da exchange.
6. Aplica transicao de estado, fill incremental ou encerramento.
7. Salva os agregados e publica eventos quando necessario.

## Roteamento De Status

- `NEW`: submete transacao `PENDING` para `SUBMITTED`; duplicatas sao
  ignoradas quando a transacao ja saiu de `PENDING`.
- `PARTIALLY_FILLED`: aplica apenas o delta ainda nao processado.
- `FILLED`: aplica o delta final e finaliza a transacao.
- `CANCELED`, `EXPIRED`, `REJECTED`: aplica transicao terminal e libera margem.

Fills podem submeter uma transacao ainda `PENDING` quando a exchange envia fill
antes do evento `NEW`.

## BUY Fill

`BuyFillHandler` aplica quantidade executada na posicao:

1. Procura posicao por `openedByTransactionId`.
2. Se nao existir, procura posicao aberta do mesmo runner/simbolo.
3. Se ainda nao existir, cria nova `Position`.
4. Associa a transacao de abertura quando necessario.
5. Salva posicao e transacao na mesma unidade transacional.

Conflito concorrente ao criar posicao e tratado recarregando a posicao aberta e
aplicando o incremento.

## SELL Fill

`SellFillHandler` reduz a posicao alvo:

1. Resolve posicao por `targetLotId` ou posicao aberta do runner/simbolo.
2. Aplica reducao incremental.
3. Destrava a posicao no fill final.
4. Cria `TransactionMatch`.
5. Publica `ExecutionConfirmedEvent`.

Posicoes ativas precisam ter `openedByTransactionId`; ausencia dessa origem e
erro de consistencia.

## Idempotencia

- `FILLED` duplicado e ignorado se a quantidade recebida nao aumentar a
  quantidade ja registrada ou se a transacao ja for terminal.
- `PARTIALLY_FILLED` duplicado e ignorado se a quantidade recebida for nula ou
  menor/igual ao executado atual.
- O efeito economico e calculado pelo incremento, nao pelo total recebido.
- Eventos financeiros derivados devem ser idempotentes no fluxo de capital.

## Componentes Principais

| Componente | Papel |
| --- | --- |
| `core/src/main/java/com/marmitt/core/application/usecase/runner/OrderConciliationUseCase.java` | Entrada do caso de uso. |
| `core/src/main/java/com/marmitt/core/application/usecase/runner/orderconciliation/ConciliationOrderUpdateExecutor.java` | Contrato compartilhado entre eventos normais e boot. |
| `core/src/main/java/com/marmitt/core/application/usecase/runner/orderconciliation/ConciliationOrderUpdate.java` | Roteia status e aplica transicoes. |
| `core/src/main/java/com/marmitt/core/application/usecase/runner/orderconciliation/BuyFillHandler.java` | Aplica fills BUY em posicao. |
| `core/src/main/java/com/marmitt/core/application/usecase/runner/orderconciliation/SellFillHandler.java` | Aplica fills SELL, matches e evento financeiro. |
| `core/src/main/java/com/marmitt/core/domain/runner/Transaction.java` | Maquina de estados da transacao. |
| `spring-application/src/main/java/com/marmitt/application/spring/config/core/RunnerConfig.java` | Implementa executor com `TransactionTemplate`. |

## Invariantes

- Transicoes de `Transaction` sao monotonicas; estados terminais nao devem ser
  reabertos.
- Eventos duplicados nao podem duplicar quantidade, PnL, match ou efeito
  financeiro.
- A soma economica dos fills aplicados nao pode exceder a execucao informada
  pela exchange.
- SELL nao pode vender quantidade indisponivel.
- Posicao travada por SELL deve ser destravada no encerramento final.

## Validacao

- `core/src/test/java/com/marmitt/core/application/usecase/runner/orderconciliation/ConciliationOrderUpdateIdempotencyTest.java`
- `core/src/test/java/com/marmitt/core/domain/runner/TransactionInvariantsTest.java`
- `spring-application/src/test/java/com/marmitt/application/spring/userdata/UserDataStreamConciliationIntegrationTest.java`
- `spring-application/src/test/java/com/marmitt/application/spring/bootstrap/OrderTerminationConciliationIntegrationTest.java`
- Comando recomendado:
  `./scripts/gradle-run.ps1 -q :core:test :spring-application:test`

## Fontes

- `docs/IMPLEMENTATION_GUIDE.md`, secoes 5, 6, 10 e 15.
- `docs/PHASE0_INVARIANTS.md`.
- `core/src/main/java/com/marmitt/core/application/usecase/runner/orderconciliation/`.
- `core/src/main/java/com/marmitt/core/domain/runner/Transaction.java`.
