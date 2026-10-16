# T42 — Mailbox por Runner e Separação dos Executores de Tick e de Ordem

**Complexidade:** Alta (concorrência no caminho do sinal)
**Responsável:** Claude
**Dependências:** nenhuma para implementar; pré-requisito para ativar estratégia remota (T41) em
ambiente real.
**Status:** Pendente

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
