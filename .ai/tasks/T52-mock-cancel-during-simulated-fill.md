# T52 — MOCK: Cancelamento de Ordem em Preenchimento Simulado

**Complexidade:** Média
**Responsável:** Claude
**Dependências:** T38 (ordens que descansam no MOCK). Achado durante a T48.
**Status:** Concluído (2026-10-21)

---

## Descrição

No MOCK, cancelar uma ordem que está sendo preenchida pelo caminho simulado (ordem MARKET ou LIMIT
marketable, executada em parciais com atraso) produz uma sequência impossível numa exchange real:

1. `cancelOrderRest` publica `CANCELED` com o `executedQuantity` do momento;
2. a simulação assíncrona continua e publica os `PARTIALLY_FILLED`/`FILLED` restantes **depois** do
   terminal;
3. o saldo do mock já foi debitado pela execução inteira, então a ordem seguinte do mesmo ativo é
   `REJECTED` por falta de saldo.

O core trata corretamente o lado dele (recusa o fill pós-`CANCELED` com
`orderConciliation: failed ... Cannot apply partial fill from status: CANCELED`), mas o MOCK fica
inconsistente consigo mesmo. Não afeta exchange real. Afeta qualquer E2E que cancele uma ordem
marketable no MOCK: o Safe Mode `CANCEL_ALL`/`PANIC_SELL`, a escalada da T48, a proteção do lote (T44)
e o `SHOULD_CANCEL` da estratégia.

**Evidência (T48, 2026-10-20):** no E2E de liquidação LIMIT com prazo de 3 ticks, a SELL LIMIT
marketable foi cancelada no meio da simulação (`CANCELED exec=0.000333`), o MOCK publicou o
`FILLED` em seguida e as três SELL MARKET seguintes foram `REJECTED`. O E2E passou a usar prazo
de 30 ticks para não disparar a escalada durante a simulação.

## Contexto técnico

- `adapter-mock/.../runtime/MockExchangeRuntime.java`
  - `submitOrderRest`: ordem que não descansa vai para `simulateOrderExecutionAsync`.
  - `simulateOrderExecutionAsync`: monta o cronograma com `simulator.buildScenarioSchedule` e
    publica evento a evento com `sleepForEvent`, **sem consultar nenhum estado de cancelamento**.
  - `cancelOrderRest`: para ordem em `restingManagedOrderIds`, quem remove a ordem do registro vence
    (cancelamento × cruzamento). Para ordem simulada não há árbitro: publica `CANCELED` sempre que o
    último snapshot não é terminal.
  - `publishMockOrderResponse`: `latestEventByOrderId.put` incondicional, então um fill tardio
    sobrescreve o snapshot `CANCELED` e a consulta REST passa a responder `FILLED`.
- `adapter-mock/.../simulator/MockOrderExecutionSimulator.java`
  - `buildScenarioEvents` / `buildScenarioSchedule`: chamam `applyBalanceForFill` para **todos** os
    fills ao montar o cronograma, antes de publicar o primeiro evento. O saldo reflete a execução
    completa desde o início.
- `.ai/flows/mock-exchange-runtime.md`, seção "Ordens Que Descansam": o árbitro único já existe para
  o caminho resting; esta task estende a mesma regra ao caminho simulado.

## Solução proposta (a validar)

Dar ao caminho simulado o mesmo árbitro do caminho resting: **quem fecha a ordem primeiro vence, e
nada é publicado depois de um terminal**.

1. **Saldo aplicado na publicação, não no planejamento.** O cronograma passa a carregar o incremento
   de cada evento, e o `applyBalanceForFill` acontece quando o evento é de fato publicado. Assim um
   cancelamento no meio só deixa debitado o que foi executado.
2. **Claim por evento.** Antes de publicar cada evento simulado, a simulação faz um
   `latestEventByOrderId.compute` que só aceita o evento se o snapshot atual não for terminal (o mesmo
   padrão de `claimAckSlot`). Recusado o claim, a simulação para.
3. **Cancelamento como claim terminal.** `cancelOrderRest` grava o `CANCELED` pelo mesmo `compute`,
   libera a reserva do restante não executado e só publica se venceu. Se a simulação já publicou o
   `FILLED`, devolve o snapshot vencedor, como no caminho resting.
4. **Snapshot monotônico.** `publishMockOrderResponse` não sobrescreve snapshot terminal.

## Riscos e pontos de atenção

- **Overrides de cenário (`MockOrderScenarioOverride`)** publicam de propósito eventos duplicados ou
  fora de ordem (`EventOrdering.REVERSE`) para testar a idempotência do core. A regra "nada depois do
  terminal" não pode suprimir esses cenários. Opção: o árbitro vale só para o cronograma padrão;
  ordens com override seguem o plano como hoje (já têm precedência sobre a decisão de resting).
- Mover o débito de saldo para a publicação muda o saldo observado **durante** a simulação. Testes
  que leem o saldo do mock no meio de uma execução precisam ser revistos.
- Com a correção, o E2E de liquidação LIMIT da T48 pode voltar ao prazo padrão de 3 ticks e provar
  o cancelamento de uma LIMIT parcialmente executada.

## Critérios de aceitação

1. Cancelar uma ordem simulada no meio do cronograma publica um único `CANCELED`, com o
   `executedQuantity` já publicado, e nenhum evento depois dele.
2. O saldo do mock fica debitado só pelo que foi executado; a reserva do restante é devolvida.
3. Se o `FILLED` vencer a corrida, o cancelamento não publica nada e devolve o `FILLED`.
4. A consulta REST da ordem nunca regride de um terminal.
5. Cenários com override (duplicados, ordem reversa) continuam publicando como hoje.
6. O E2E de liquidação LIMIT da T48 passa com o prazo padrão de 3 ticks, cobrindo LIMIT parcial →
   `CANCELED` → MARKET do restante.

## Validação

- Unitários do `MockExchangeRuntime`: cancelamento antes do primeiro fill, entre parciais e depois do
  `FILLED`; saldo e reserva em cada caso.
- `./scripts/gradle-run.ps1 :adapter-mock:test` e os E2E de cenário (`SafeModeE2ETest`,
  `PositionProtectionE2ETest`, `Scenario*E2ETest`).

## Entrega (2026-10-21)

- **Saldo na publicação:** `MockScheduledOrderEvent` ganhou `settlement` (débito/crédito do fill ou
  devolução da reserva numa falha simulada), executado quando o evento é publicado. Cópias duplicadas
  não liquidam (`duplicate`). `buildScenarioSchedule` devolve `MockOrderSchedule` (eventos + se a
  reserva foi feita) e é chamado **na submissão**, antes do ACK REST, como a ordem que descansa: um
  cancelamento logo depois do ACK sempre encontra a reserva feita.
- **Árbitro por ordem:** `SimulatedOrder` no runtime; cada evento do cronograma é checado e publicado
  sob o lock da ordem, e o cancelamento roda sob o mesmo lock. Terminal publicado vence o
  cancelamento (devolve o terminal); senão a ordem é marcada cancelada, o cronograma para, a reserva
  da parte não executada volta (`releaseUnexecutedReservation`, que substitui
  `releaseReservationForOpenOrder` também no caminho resting) e sai um único `CANCELED` com o
  executado.
- **Snapshot monotônico:** `publishMockOrderResponse` não troca snapshot terminal; duplicatas e
  eventos fora de ordem continuam sendo publicados. Os testes de override que provavam a entrega do
  evento atrasado pela consulta REST passaram a ler o histórico publicado
  (`MockExchangeAdapter.publishedOrderEvents`, via `awaitMockOrderStatus`).
- **Overrides:** o plano segue igual (duplicatas, ordem reversa); só o cancelamento externo o
  interrompe. Decisão: o árbitro vale para todo cronograma, não só o padrão, porque o cancelamento
  externo de uma ordem com override também não pode deixar fills depois do `CANCELED`.
- **Testes:** `MockExchangeRuntimeSimulatedCancelTest` (5: cancelamento antes do primeiro fill, entre
  parciais, depois do `FILLED`; reverso mantém o terminal na consulta; duplicatas liquidam uma vez);
  testes do simulador ajustados ao saldo na publicação; `SafeModeE2ETest` ganhou a liquidação LIMIT com
  prazo padrão de 3 ticks (cancelada no meio dos parciais, MARKET do restante, sem recusa, executado =
  casado). O teste de 30 ticks continua provando o caminho em que a LIMIT preenche inteira.
- **Limite conhecido:** a reserva devolvida de uma BUY parcial é calculada ao preço da ordem; com
  slippage no fill o débito real difere um pouco e o `MockBalanceStore` limita a devolução ao
  reservado. Já era assim no fill completo.

## Fontes

- `.ai/tasks/T48-panic-sell-limit-protection.md` (Entrega → Riscos residuais)
- `.ai/flows/mock-exchange-runtime.md`
