# T38 — Testes E2E das Estratégias de Cenário

**Complexidade:** Média
**Responsável:** Claude
**Dependências:** T35 (estratégias de cenário + `maxCycles`), T34 (`limitPrice`), T32 (`SHOULD_CANCEL`)
**Status:** Pendente

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
| (opcional, Opção B) `adapter-mock/.../simulator/MockOrderExecutionSimulator.java` | Honrar resting vs. marketable por `limitPrice` |
| Suporte de teste comum (fixtures/helpers) | Registro de estratégia bounded, driver de ticks, consultas JDBC de transação/posição/saldo, leitura de métrica |

Reusar ou generalizar os helpers de `ProcessTradeSignalMockIntegrationTest`/
`MockOrderOverrideIntegrationTestSupport` em vez de duplicar.

---

## Riscos / pontos de atenção

- **MOCK sempre-fill (pré-requisito acima):** sem a Opção A ou B, os cenários 1 e 2 não descansam.
  Não escrever o e2e desses dois antes de resolver isso.
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
