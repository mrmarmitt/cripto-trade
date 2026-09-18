# T38 — Testes E2E das Estratégias de Cenário

**Complexidade:** Média
**Responsável:** Claude
**Dependências:** T35 (estratégias de cenário + `maxCycles`), T34 (`limitPrice`), T32 (`SHOULD_CANCEL`)
**Status:** Em andamento — mock resting entregue (fase 1); e2e dos cenários 3 e 4 entregues (fase 2);
cenários 1 e 2 pendentes (fase 3)

---

## Descrição

A T35 entregou quatro estratégias de cenário determinísticas e o teto de ciclos (`maxCycles`) que
lhes dá **início e fim determinísticos**. Falta a validação end-to-end: subir a aplicação, ativar
cada estratégia com um teto pequeno (ex.: `maxCycles = 1`), injetar ticks e **validar que o ciclo
esperado ocorreu por inteiro e que nenhuma transação além da necessária foi criada**.

Cada teste é um "start→end" fechado: a estratégia executa N ciclos, converge para `HOLD`, e o teste
assere o estado final (transações, posições/lotes, saldo, métrica de outcome) e a **ausência de
efeito extra** ao continuar injetando ticks.

---

## Pré-requisito crítico: comportamento do MOCK (resting vs. marketable)

Hoje o `MockOrderExecutionSimulator.buildScenarioEvents` (caminho default, sem override) **sempre
preenche** a ordem: 100% `FILLED` no preço solicitado, independentemente de o `limitPrice` estar
longe do mercado. Ele **não modela uma ordem que descansa** (`NEW`/`PENDING` sem preencher). Não-fill
só acontece via:

- `MockOrderScenarioOverride` — schedule determinístico por ordem (`NEW`→`CANCELED`, parciais, etc.);
- ratios aleatórios de `cancel`/`expire` no `MockScenarioConfig` (não determinístico).

**Consequência:** os cenários 1 e 2 (ordem que descansa) **não** se comportam como em testnet sob o
MOCK default — a BUY/SELL "descansando" seria preenchida. Decisão necessária antes de codar:

- **Opção A (pragmática, recomendada):** dirigir o MOCK por `MockOrderScenarioOverride` por cenário
  — ex.: para o resting-buy-cancel, agendar a ordem como `NEW` que permanece aberta até o
  cancelamento chegar (`CANCELED`). Reusa a infra já existente (`MockOrderOverrideIntegrationTestSupport`,
  `OrderLifecycleMockIntegrationTest`). O e2e valida o fluxo estratégia→pipeline→conciliação com
  desfecho determinístico, sem depender de preço.
- **Opção B (fiel, maior):** ensinar o MOCK a honrar marketable-vs-resting comparando `limitPrice`
  a um preço de referência do feed (resting quando não-marketable; fill quando cruza). Mais próximo
  do testnet real, mas é mudança de comportamento no `adapter-mock` com blast radius próprio
  (afeta outros testes que assumem always-fill).

Registrar a escolha nesta task antes da implementação. Independentemente da opção, os cenários 3
(round trip) e 4 (over-allocation) já são determinísticos no MOCK atual (fill garantido / rejeição
por capital).

### Decisão registrada (2026-09-18): Opção B

Escolhida a **Opção B** — o MOCK passou a honrar marketable-vs-resting. Motivo: a Opção A é uma
escada descartável, enquanto a B é infraestrutura que a **T37** (normalização de preço side-aware,
hoje inobservável sob always-fill) e os futuros e2e de estratégia out-of-process também consomem.

A spec original subestimava o trabalho: além do `if` de marketable, faltavam duas peças que não
existiam. Como foram resolvidas:

- **Não havia preço de referência.** `lastPrice` era campo privado da inner class `FeedTask` e os
  testes de integração sequer usam o feed (injetam `MarketDataDto` direto via `ProcessTradeSignalPort`).
  Criado `MockReferencePriceStore`, alimentado pelo feed (`setPriceListener`) e por
  `MockExchangeRuntime.seedReferencePrice` / `MockExchangeAdapter.seedReferencePrice` nos testes.
- **O modelo de execução era script pré-computado.** `submitOrderRest` montava todo o ciclo de vida
  na submissão. Ordem que descansa exige parar no `NEW` e esperar sinal externo, então a colocação
  de ordem resting passou a ser síncrona (validação + reserva + registro antes do ACK REST), com
  apenas o callback `NEW` assíncrono.

**Backward compatibility por construção:** sem preço de referência conhecido para o símbolo, o
comportamento é o de sempre (preenche). Nenhum teste existente precisou ser editado — as 166 do
`spring-application` e as do `adapter-mock` passam sem alteração.

Blast radius medido antes da decisão: 12 arquivos de teste citam `FILLED`, mas 6 usam
`MockOrderScenarioOverride` (imunes, o override tem precedência sobre resting) e 2 são de outro
fluxo; exposição real ~4 arquivos, nenhum quebrado.

Consequência para os e2e: os cenários 1 e 2 passam a descansar de verdade, bastando semear o mesmo
preço que o teste injeta no pipeline de sinal.

---

## Contexto técnico

### Harness de integração existente

- `AbstractIntegrationTest` sobe o contexto Spring completo com Postgres via Testcontainers.
- `ProcessTradeSignalMockIntegrationTest` é o padrão de referência: cria portfolio+runner, publica
  `MarketDataDto` via `ProcessTradeSignalPort`, e consulta `transactions`/`positions` por JDBC.
- **Registro de estratégia em runtime:** `StrategyRepositoryPort.registerStrategy(...)` permite
  registrar uma instância com `ScenarioStrategyConfig.boundedConfig(n)` sem tocar produção nem
  depender da flag `ctrade.scenario-strategies.enabled` (padrão já usado no teste do T34).
- Métricas: `signal.evaluated.total{runnerId,decision}` via `MeterRegistry` para asserir outcomes
  (`BUY`/`SELL`/`CANCEL`/`REJECTED_CAPITAL`).

### Ativação por cenário

Registrar a estratégia com `boundedConfig(1)` (ou N pequeno), criar um runner apontando para o
`strategyId` dela, ativar, e dirigir ticks até a estratégia holdar (ou por um número fixo de ticks).

---

## Solução proposta

Um teste e2e por estratégia (arquivo em `spring-application/src/test/.../bootstrap/`), cada um com
teto de ciclos pequeno, driver de ticks e asserts de estado final + ausência de excesso.

| Cenário | Sequência esperada (maxCycles=1) | Asserts principais |
|---|---|---|
| `RestingBuyCancelStrategy` | BUY `PENDING` (descansa) → `CANCELED` | 1 transação BUY terminando `CANCELED`; capital reservado e **liberado** (available restaurado); sem lote aberto; `decision=CANCEL` |
| `FilledBuyRestingSellCancelStrategy` | BUY `FILLED` → SELL `PENDING` (descansa) → `CANCELED` | 1 BUY `FILLED` + 1 SELL `CANCELED`; **1 lote aberto** remanescente (por design); lock de posição liberado |
| `ImmediateRoundTripStrategy` | BUY `FILLED` → SELL `FILLED` | 1 BUY + 1 SELL ambos `FILLED`; **posição fechada**; `transaction_match`/PnL materializado |
| `OverAllocationRejectStrategy` | BUY recusado na reserva | **0 transações persistidas**; `decision=REJECTED_CAPITAL` incrementado; saldo intacto |

**Asserção comum a todos:** após a estratégia holdar, injetar ticks adicionais **não** cria novas
transações nem altera saldo/posição (prova o "sem excesso").

---

## Arquivos a criar / modificar

| Arquivo | Mudança |
|---|---|
| `spring-application/src/test/.../bootstrap/ScenarioRestingBuyCancelE2ETest.java` | E2E cenário 1 |
| `spring-application/src/test/.../bootstrap/ScenarioFilledBuyRestingSellCancelE2ETest.java` | E2E cenário 2 |
| `spring-application/src/test/.../bootstrap/ScenarioImmediateRoundTripE2ETest.java` | E2E cenário 3 |
| `spring-application/src/test/.../bootstrap/ScenarioOverAllocationRejectE2ETest.java` | E2E cenário 4 |
| ~~(opcional, Opção B) `adapter-mock/.../simulator/MockOrderExecutionSimulator.java`~~ | **Entregue** — ver "Decisão registrada" |
| Suporte de teste comum (fixtures/helpers) | Registro de estratégia bounded, driver de ticks, consultas JDBC de transação/posição/saldo, leitura de métrica |

Reusar ou generalizar os helpers de `ProcessTradeSignalMockIntegrationTest`/
`MockOrderOverrideIntegrationTestSupport` em vez de duplicar.

---

## Riscos / pontos de atenção

- ~~**MOCK sempre-fill (pré-requisito acima):**~~ resolvido pela Opção B. O e2e precisa semear o
  preço de referência com o mesmo valor do tick injetado, senão estratégia e exchange enxergam
  mercados diferentes — usar um helper único que faça as duas coisas.
- **Assíncrono/timing:** o pipeline despacha e concilia em background (como no teste atual, que faz
  polling com timeout). Usar espera com deadline, não `sleep` fixo, e asserir por convergência.
- **Isolamento entre testes:** truncar tabelas e resetar o MOCK no `@BeforeEach` (padrão já existente).
- **`maxCycles` e estado da instância:** a instância registrada carrega o contador; registrar uma
  instância nova por teste (não reusar a global) para o start/end ser limpo.
- **Não empilhar / SINGLE:** confirmar que o teto + política SINGLE produzem exatamente o número de
  transações esperado; o assert de "sem excesso" cobre regressões aqui.
- **Custo de suíte:** e2e com Testcontainers é caro; manter os cenários enxutos (teto pequeno) e,
  se necessário, agrupar num único arquivo por afinidade para reduzir bootstraps.

---

---

## Achados dos e2e (2026-09-18)

Os dois primeiros e2e (cenários 3 e 4) expuseram um bug real no core e um resíduo de capital.

### 1. SELL FIFO não encontrava a própria posição no fill — **corrigido**

`SellFillHandler.resolvePosition` resolvia a posição por `targetLotId` ou, no fallback, por
**posição OPEN** (`findOpenPositionByRunnerIdAndSymbolForUpdate`, que ainda exige
`locked_by_transaction_id IS NULL`). Uma SELL FIFO não carrega `targetLotId` — `TradeIntentFactory`
copia `signal.targetLotId()`, que é null por contrato ("null ⇒ FIFO") — e o lock move a posição de
`OPEN` para `CLOSING`. Resultado: **nenhum dos dois caminhos achava a posição**, todo fill de SELL
FIFO morria com `IllegalStateException: No position found for SELL fill`, deixando a SELL em
`SUBMITTED` e o lote travado em `CLOSING` — capital e posição em limbo, sem DLQ.

Atingia qualquer estratégia que venda sem designar lote, incluindo `ImmediateRoundTripStrategy` e
`FilledBuyRestingSellCancelStrategy` da T35. Passou despercebido porque os testes de SELL existentes
usam override e caminho com lote explícito.

Correção: `resolvePosition` passou a consultar a posição travada por aquela transação
(`findPositionLockedByTransactionIdForUpdate`, novo no port e no adapter JDBC) entre o `targetLotId`
e o fallback antigo. `locked_by_transaction_id` é o vínculo autoritativo: liquida exatamente o lote
que aquela venda reservou. É o mesmo raciocínio que `TerminationHandler.resolvePosition` já aplicava
ao considerar posições em `CLOSING` para desfazer o lock.

### 2. Resíduo de capital reservado por ciclo — **não corrigido, ver T36**

Depois de um round trip completo (BUY `FILLED`, SELL `FILLED`, posição `CLOSED`, match
materializado), o `global_balances` fica com `reserved = 0.06630000` — exatamente a taxa estimada da
BUY (`0.001 × 66300 × 0.0010`). O restante do saldo fecha: `available + reserved` menos o capital
inicial é igual ao PnL realizado. Ou seja, a reserva da BUY não é devolvida por inteiro; cada ciclo
deixa a estimativa de fee presa em `reserved`.

Não corrigido aqui de propósito: é aritmética de reserva/devolução, toca invariantes do
`PHASE0_INVARIANTS` e cai exatamente no escopo da **T36**, que já precisa decidir entre implementar
o safety buffer com a devolução do excedente (§7.2.3 do `IMPLEMENTATION_GUIDE`) ou remover o buffer
do design. Recomendação: tratar esse resíduo como evidência concreta para a T36, não como task nova.

Por isso o e2e do cenário 3 assere os critérios da tabela acima (BUY+SELL `FILLED`, posição fechada,
PnL materializado, quantidade casada) e **não** assere `reserved == 0`.

---

## Critérios de aceitação

1. Decisão registrada sobre o comportamento do MOCK para ordens resting (Opção A ou B).
2. Um e2e por estratégia sobe a aplicação, ativa a estratégia com teto de ciclos e valida a
   sequência esperada de transações/estado (tabela acima).
3. Cada e2e assere que, após o teto, ticks adicionais **não** criam transações nem alteram
   saldo/posição.
4. Outcomes de `signal.evaluated.total{decision}` conferem por cenário (`CANCEL`, `REJECTED_CAPITAL`, etc.).
5. Testes determinísticos e isolados (truncate + reset MOCK por teste), sem `sleep` fixo.
6. Nenhuma mudança de produção além da eventual Opção B no `adapter-mock` (se escolhida).

---

## Fontes

- `.ai/tasks/T35-testnet-scenario-strategies.md` (estratégias + `maxCycles`/`boundedConfig`).
- `.ai/flows/runner-signal-processing.md` (outcomes de `signal.evaluated.total`, guardas de execução).
- `spring-application/src/test/.../bootstrap/ProcessTradeSignalMockIntegrationTest.java` (harness + registro de estratégia em runtime).
- `spring-application/src/test/.../bootstrap/OrderLifecycleMockIntegrationTest.java` e `MockOrderOverrideIntegrationTestSupport.java` (overrides determinísticos do MOCK).
- `adapter-mock/.../simulator/MockOrderExecutionSimulator.java` (default always-fill; sem resting).
- `docs/PHASE0_INVARIANTS.md` (reserva/lock/PnL/match).
