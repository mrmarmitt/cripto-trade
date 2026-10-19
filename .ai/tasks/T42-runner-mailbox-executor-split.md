# T42 — Mailbox por Runner e Separação dos Executores de Tick e de Ordem

**Complexidade:** Alta (concorrência no caminho do sinal)
**Responsável:** Claude
**Dependências:** nenhuma para implementar; pré-requisito para ativar estratégia remota (T41) em
ambiente real.
**Status:** Concluído (2026-10-19) — ver "Entrega".

---

## Descrição

Hoje market data e user data (fills, cancels, rejects) dividem o mesmo pool:

- `ProcessMessageEventListener` e `ProcessUserMessageEventListener` usam
  `@Async("messageProcessingExecutor")`.
- `AsyncConfig.messageProcessingExecutor`: core 2, max 4, fila 500, `CallerRunsPolicy`.
- O tick segue síncrono dentro da task: parsing → `PortfolioStrategyRunnerPriceUpdateListener` →
  `ProcessTradeSignalUseCase.execute`, que avalia os runners do símbolo **em série**.

Consequências, que pioram quando a avaliação passar a envolver rede (T41):

1. **Fill atrás de tick.** Avaliação lenta ocupa as threads; a conciliação de fills espera na mesma
   fila. Capital e posição ficam desatualizados enquanto a estratégia é chamada.
2. **Backpressure no WebSocket.** Com a fila cheia, `CallerRunsPolicy` executa o trabalho na thread
   que publicou o evento (leitura do socket).
3. **Ticks fora de ordem.** Com até 4 threads, ticks do mesmo símbolo podem ser avaliados
   concorrentemente/fora de ordem — estratégias com estado (média móvel) veem uma série embaralhada.

O IG §12.1–12.2 já especifica a solução (Actor/Mailbox por runner, capacidade 1, processamento
sequencial; nota #23 marcada "RESOLVIDA"), mas ela **não existe no código** (ver T39).

---

## Solução proposta

1. **Executores separados.** `userDataProcessingExecutor` exclusivo para user data/conciliação;
   market data em executor próprio. Fill nunca espera tick.
2. **Mailbox por runner, capacidade 1, latest-wins.** O tick é roteado (por símbolo/exchange) e
   depositado na mailbox de cada runner elegível; se já houver tick pendente não processado, ele é
   **substituído** (coalescência). Cada runner é drenado por no máximo uma thread por vez, preservando
   ordem e isolando runners lentos dos demais.
3. **Sem `CallerRunsPolicy` no caminho de market data.** Descartar/coalescer em vez de bloquear a
   thread do socket.
4. **Métricas:** `runner.mailbox.coalesced{runnerId}` (ticks substituídos),
   `runner.signal.processing.time{runnerId}`, idade do tick ao iniciar avaliação. Reaproveitar o
   desenho de backpressure do IG §12.8 (alerta, não suspensão).

### Fronteira

A política (latest-wins, sequencial por runner) é regra de domínio do runner e pode morar no `core`
como componente sem framework; executores e threads concretos ficam no `spring-application`. Avaliar
na implementação se o `ProcessTradeSignalUseCase.execute(MarketDataDto)` passa a ser
`execute(runnerId, MarketDataDto)` atrás da mailbox.

---

## Riscos / pontos de atenção

- **Mudança de concorrência em área de risco alto:** invariantes de reserva, lock de SELL e
  monotonicidade (`docs/PHASE0_INVARIANTS.md`) precisam continuar verdes; T19 (striped lock na
  conciliação) não pode regredir.
- **Testes de integração** que injetam `MarketDataDto` direto via `ProcessTradeSignalPort` e esperam
  efeito síncrono podem precisar de espera por convergência.
- **Kill switch / halt** deve drenar ou descartar mailboxes de runners `HALTED`.
- Coalescer muda a semântica para estratégias que precisam de **todo** tick; registrar a decisão
  (o IG já a assume) e expor a métrica de coalescência.

---

## Critérios de aceitação

1. User data e market data em executores distintos; teste prova que um tick lento não atrasa a
   conciliação de um fill.
2. Ticks de um mesmo runner nunca são avaliados concorrentemente nem fora de ordem.
3. Com avaliação mais lenta que o feed, o runner avalia sempre o tick mais recente e a métrica de
   coalescência incrementa; a thread do socket não é usada para processamento.
4. Suítes `:core:test` e `:spring-application:test` verdes, incluindo invariantes da Fase 0–2.
5. `.ai/flows/runner-signal-processing.md` e `.ai/flows/websocket-event-routing.md` atualizados;
   IG §12 marcado como implementado.

---

## Fontes

- `spring-application/src/main/java/com/marmitt/application/spring/config/AsyncConfig.java`.
- `spring-application/src/main/java/com/marmitt/application/spring/handler/ProcessMessageEventListener.java`
  e `ProcessUserMessageEventListener.java`.
- `core/src/main/java/com/marmitt/core/application/listener/runner/PortfolioStrategyRunnerPriceUpdateListener.java`.
- `core/src/main/java/com/marmitt/core/application/usecase/runner/processsignal/ProcessTradeSignalUseCase.java`.
- `docs/IMPLEMENTATION_GUIDE.md` §12.1, §12.2, §12.8.
- `.ai/tasks/T19-conciliation-striped-lock.md`.

---

## Achados da T39 (2026-10-16)

- **Timeout por fase (IG §12.7):** processamento total do sinal, reserva e dispatch não têm timeout
  próprio além do cliente HTTP. Incluir junto da mailbox.
- **Stale signal check (IG §12.3):** descartar o tick/decisão mais velho que o limite ao iniciar a
  avaliação (a idade vem do contrato revisado na T40).

---

## Entrega (2026-10-19)

### Decisões registradas

- **Três executores (implementação):** `marketDataExecutor` (thread única: preserva a ordem de
  chegada; só faz parsing e roteia para as mailboxes), `userDataExecutor` (fills; nunca descarta —
  `CallerRunsPolicy` aplica pressão no socket de user data, pois fill não pode se perder) e
  `runnerSignalExecutor` (`runner.signal.threads`, avalia os runners). O `messageProcessingExecutor`
  compartilhado foi removido.
- **Market data não descarta mensagem:** o canal MARKET também carrega respostas de ordem da WS API
  (`OrderDataDto` conciliado pelo mesmo handler). A coalescência acontece só na mailbox de cada
  runner. A fila de parsing é grande (10.000); se transbordar, a mensagem é descartada com log ERROR
  em vez de bloquear a thread do socket (a ordem ainda converge pelo `executionReport` do user data).
- **Mailbox no core (`RunnerMailbox`), sem framework:** capacidade 1, latest-wins, um runner por vez,
  descarte de tick com timestamp anterior ao último aceito, e reagendamento ao fim da fila depois de
  cada avaliação (runner com feed intenso não monopoliza thread). Recebe só um `java.util.concurrent.Executor`.
- **`execute(MarketDataDto)` só roteia;** a avaliação (`evaluateTick`) relê o runner — kill switch,
  encerramento ou reconciliação que aconteceram enquanto o tick esperava descartam a avaliação — e lê
  o nível do Safe Mode por avaliação.
- **Stale check (IG §12.3):** tick mais velho que `runner.signal.stale-tick-threshold-ms` (5000, padrão
  do IG) ao começar a avaliação é descartado. A idade é medida desde o recebimento (todos os
  produtores usam `Instant.now()`), então não depende do relógio da exchange; `MarketDataDto.timestamp`
  passou a ser obrigatório.
- **Timeout por fase (IG §12.7) vira alerta, não aborto:** abortar uma avaliação no meio pode deixar
  persist/reserva/dispatch pela metade. A duração vai para `runner.signal.processing.time` e acima de
  `runner.signal.slow-processing-warn-ms` (1000) gera WARN. O timeout da chamada remota à estratégia
  é do cliente HTTP da T41.
- **Uma métrica de descarte:** `runner.ticks.discarded{runnerId, reason=COALESCED|STALE|OUT_OF_ORDER}`,
  pelo mesmo `SignalMetricsPort`.
- **Comandos do operador (T49) seguem síncronos** na thread HTTP, concorrentes com a mailbox; a
  duplicidade de venda continua barrada pelo lock de posição.

### Validação

- `:core:test` 194 — `RunnerMailboxTest`: coalescência no mais recente (2 descartes contados), tick
  fora de ordem descartado, runner ocupado cede a thread a outro runner, 200 ticks com pool de 4 threads
  sem avaliação paralela do mesmo runner, em ordem crescente e fora da thread que entrega.
- `:spring-application:test` 199 — `MessageExecutorIsolationTest`: com o market data travado, o fill é
  conciliado na hora, e nenhum dos dois roda na thread que publica o evento; `RunnerMailboxE2ETest`:
  tick velho descartado sem consultar a estratégia. As demais suítes (Fases 0–4, T38, T45, T49)
  passaram sem ajuste — já esperavam convergência.
- `:adapter-binance:test` 76, `:adapter-mock:test` 24.

### Riscos residuais

- **Thread única de market data:** parsing lento de um adapter atrasa os demais símbolos. Hoje o
  parsing é leve; se virar gargalo, particionar por conexão.
- **Estratégia que precisa de todo tick** perde ticks coalescidos (decisão já assumida pelo IG); a
  métrica mostra quanto.
