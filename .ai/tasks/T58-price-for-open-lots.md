# T58 — Preço para Lotes Abertos sem Runner Operando

**Complexidade:** Baixa
**Responsável:** Claude
**Dependências:** T56 (coordenador de demanda de market data), T43 (drawdown e mark-to-market), T55
(estado consolidado). Origem: achado F8 da rodada 3 da T53.
**Status:** Pendente — decisões registradas; pronta para implementação.

---

## Descrição

A demanda de market data (T56) vem só dos runners `ACTIVE` e `TERMINATING`. Um lote aberto de runner
parado (`HALTED`, `PAUSED`, `CREATED`) não gera demanda. Depois de um restart, ou com todos os runners
do símbolo parados, o preço desse símbolo **não chega**, e o que depende dele fica cego:

- **Drawdown (T43):** `EvaluateDrawdownUseCase` pula o portfolio inteiro enquanto houver lote sem preço
  (`WARN drawdown: no market price ... skipped` a cada avaliação, 10s). Visto na rodada 3: 3 vezes em
  20s, até o cenário 3 ser retomado e o feed abrir.
- **PnL (`GET /api/portfolios/{id}/pnl`):** `QueryPortfolioUseCase` soma o flutuante só das posições com
  preço; lote sem preço entra como zero **sem aviso**. Um agente lê "flutuante 0" como "lote no zero a
  zero" — viola o princípio de estado autoexplicativo (`.ai/product-direction.md`).
- **Estado consolidado:** `MARKET_DATA_STALE` só olha símbolos com runner `ACTIVE`; o lote sem preço não
  aparece em nenhuma verificação.

O último preço em memória (`LatestPricePort`) só some no restart: com o processo de pé, o último
preço conhecido continua lá depois do `halt` (por isso a rodada 3 só viu o aviso na subida). Mas esse
preço envelhece sem que ninguém perceba.

## Contexto técnico

- `core/.../usecase/websocket/EnsureMarketDataFeedUseCase` — demanda por runner `ACTIVE`/`TERMINATING`,
  `(fonte, símbolo)` para cada fonte de `allowedMarketDataSources`; abre conexão ou reassina símbolo sem
  tick há mais que `market-data-stale-ms`.
- `core/.../usecase/portfolio/PortfolioMarkToMarket` — `Valuation(unrealizedPnl, unpriced)`; usado pelo
  drawdown (pula se incompleto) e pelo PnL (ignora `unpriced`).
- `core/.../usecase/portfolio/QueryPortfolioUseCase.getPnl` → `PortfolioPnlDto` (`realizedPnL`,
  `unrealizedPnL`, `totalPnL`).
- `core/.../usecase/operations/InspectOperationalStateUseCase.marketDataStale`.
- `StrategyRunnerRepositoryPort.findActivePositionsByPortfolioId` (OPEN + CLOSING) e
  `findOpenPositionsByRunnerId`.
- Runner parado **não** processa tick (`RunnerSignalPolicy.canProcessRunner`): proteção do lote (T44) e
  liquidação do Safe Mode não rodam para ele, com ou sem preço. Fica assim (decisão 2).

## Solução proposta

1. **Demanda inclui lote aberto (core).** No `EnsureMarketDataFeedUseCase`, além dos runners
   operacionais, todo runner não arquivado com lote `OPEN`/`CLOSING` demanda `(fonte, símbolo)` pelas
   fontes do próprio runner. Mesmo caminho de abertura/reassinatura; nenhuma regra nova de feed. O tick
   continua sem efeito no runner parado (o roteamento filtra por status), só atualiza o último preço.
   Stream de usuário segue só para runners operacionais (lote parado não gera ordem).
2. **PnL autoexplicativo (core + contrato).** `PortfolioPnlDto` ganha `unpricedPositions` (quantos lotes
   ficaram fora do flutuante por falta de preço) e `priceAsOf` (o tick mais antigo usado). Campo novo,
   aditivo: quem lê hoje não quebra. Com `unpricedPositions > 0`, `unrealizedPnL` é parcial e o
   OpenAPI diz isso.
3. **Estado consolidado.** `MARKET_DATA_STALE` passa a cobrir também os símbolos com lote aberto, com
   motivo por símbolo (`demand: ACTIVE_RUNNER` ou `OPEN_LOT`): `OPEN_LOT` é `WARN`, `ACTIVE_RUNNER`
   segue `CRITICAL` (decisão 1).
4. **Drawdown:** sem mudança de regra (equity incompleto continua pulado; decisão da T43). Com o item 1,
   a janela cega cai para o primeiro ciclo do coordenador (5s após subir). O `WARN` repetido a cada 10s
   vira um `WARN` na primeira vez e `DEBUG` enquanto o mesmo lote seguir sem preço, para não inundar o
   log.
5. **Runbook:** `#market-data-stale` cobre o motivo `OPEN_LOT`; a consulta de PnL explica
   `unpricedPositions`.

## Decisões registradas (2026-10-21)

1. **Preço parado para lote de runner parado é `WARN`** em `MARKET_DATA_STALE` (ninguém opera, mas o
   drawdown e o PnL ficam cegos). Runner `ACTIVE` continua `CRITICAL`.
2. **`halt` não negocia nada:** runner parado não roda stop do lote (T44) nem liquidação do
   `PANIC_SELL`. É o comportamento atual e fica; esta task só traz o preço para valorar o lote.

## Fora do escopo

- Cancelar assinaturas sem demanda (decisão da T56).
- Mudar a regra do drawdown com equity incompleto.
- Proteção/liquidação para runner parado (`halt` não negocia nada, decisão 2).

## Riscos

- **Mais assinaturas:** cada símbolo com lote aberto passa a ser assinado mesmo sem runner operando;
  com poucos símbolos, custo desprezível (1 tick/s por símbolo na Binance).
- **Fonte do lote:** a fonte vem das `allowedMarketDataSources` do runner dono do lote; runner
  arquivado com lote aberto não gera demanda (lote órfão é caso do `POSITION_LOCK_ORPHAN`/runbook).
- **Contrato do PnL:** campos novos são aditivos; nenhum campo existente muda de significado, mas
  `unrealizedPnL` passa a ser declaradamente parcial quando `unpricedPositions > 0`.

## Critérios de aceitação

1. Restart com todos os runners `HALTED` e lote aberto: o coordenador assina o símbolo no primeiro
   ciclo e o drawdown do portfolio volta a ser avaliado sem nenhum runner ativo.
2. `GET /api/portfolios/{id}/pnl` com lote sem preço informa `unpricedPositions > 0`; com preço, `0` e
   `priceAsOf` preenchido.
3. `MARKET_DATA_STALE` acusa símbolo com lote aberto sem tick com motivo `OPEN_LOT` em `WARN`.
4. Runner parado continua sem processar tick (nenhuma ordem nova, nenhuma proteção disparada).
5. O aviso de drawdown sem preço não se repete a cada ciclo para o mesmo lote.

## Validação

- Unitários: `EnsureMarketDataFeedUseCaseTest` (lote aberto de runner `HALTED` gera demanda; runner
  arquivado não; stream de usuário não), `QueryPortfolioUseCase` (PnL com e sem preço),
  `InspectOperationalStateUseCaseTest` (`OPEN_LOT`).
- Integração com o MOCK: runner `HALTED` com lote aberto, coordenador ligado, preço chega e nenhuma
  transação é criada.
- Testnet: repetir o restart da rodada 3 (runners parados, 2 lotes abertos) e conferir que o `WARN` de
  drawdown não aparece depois do primeiro ciclo.

## Fontes

- `.ai/tasks/T53-testnet-soak.md` (rodada 3, achado F8)
- `.ai/tasks/T56-market-data-demand.md`, `.ai/tasks/T43-drawdown-daily-loss-breaker.md`,
  `.ai/tasks/T44-strategy-independent-position-protection.md`
- `.ai/product-direction.md` (estado autoexplicativo)
