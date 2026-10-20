# T48 — Proteção LIMIT na Liquidação do PANIC_SELL

**Complexidade:** Média
**Responsável:** Claude
**Dependências:** T45 (PANIC_SELL com MARKET), T44 (`PositionProtection` como modelo de política no runner),
T49 (endpoint de limites do runner), T37 (normalização de preço side-aware).
**Status:** Pendente — decisões de negócio registradas; pronta para implementação.

---

## Descrição

A T45 entregou o PANIC_SELL com ordem **MARKET**, por decisão do mantenedor: dá saída garantida, mas
num livro raso a venda pode executar muito abaixo do último preço. Esta task adiciona uma **política de
liquidação por runner**: o runner pode optar por liquidar com SELL **LIMIT** a um afastamento máximo do
último preço e, se ela não preencher, escalar para MARKET.

## Decisões registradas (2026-10-20)

1. **Quando × como.** O *quando* do PANIC_SELL continua sendo do portfólio (`Portfolio.safeModeStatus`,
   manual ou gatilhos da T25/T43). O *como* é do runner: cada runner liquida os próprios lotes, no
   próprio símbolo, e a liquidez varia por símbolo.
2. **A configuração mora no cadastro do runner, não no contrato da estratégia.** O PANIC_SELL roda
   justamente quando algo deu errado — inclusive com a estratégia remota indisponível —, então a saída
   não pode depender dela. A forma de liquidar em emergência é proteção do sistema (core), não alpha.
3. **Padrão MARKET.** Runner sem configuração mantém o comportamento da T45.
4. **Com LIMIT:** preço `último × (1 − afastamento)`, afastamento padrão **1%**; sem preenchimento em
   **3 ticks**, cancela e **escala para MARKET** (sem reenvio de nova LIMIT).
5. **Preço velho.** Não é preciso regra extra: o tick que chega ao `SafeModeHandler` já passou pelo
   descarte de `staleTickThreshold` em `ProcessTradeSignalUseCase` (IG §12.3), então o preço usado é
   sempre recente. Sem tick nenhum, nada é liquidado — igual ao MARKET de hoje (ver Riscos).

## Contexto técnico

- `core/.../processsignal/SafeModeHandler.java` — a cada tick em `CANCEL_ALL`/`PANIC_SELL`:
  `cancelRestingOrders` cancela **toda** LIMIT em voo do runner; `liquidationSignals` gera venda do lote
  inteiro para cada posição sem lock. Stateless: o nível persistido torna a execução convergente.
- `ProcessTradeSignalUseCase.enforceSafeMode` — envia cada liquidação com
  `tradeIntentFactory.liquidationOrderType()` (MARKET).
- `TradeIntentFactory.liquidationSignal` / `liquidationOrderType` — fonte única das saídas decididas
  pelo core: PANIC_SELL (T45), fechar posição pelo admin (T49) e proteção do lote (T44).
- `core/.../domain/runner/PositionProtection.java` — record de política do runner (T44), persistido em
  `strategy_runners` (`V15__runner_position_protection.sql`) e alterado por
  `UpdateRunnerLimitsRequest` → `ManageRunnerLifecycleUseCase.updateLimits` → `AdminController`.
- `SellFillHandler` — fill parcial de SELL reduz a quantidade do lote; cancelamento libera o lock; resto
  abaixo do mínimo vendável segue a política de dust já existente.
- `Transaction` não registra a origem (estratégia × Safe Mode): não há como distinguir, só pelo estado
  persistido, uma SELL LIMIT da estratégia de uma LIMIT de liquidação.

## Solução proposta

1. **Domínio (`core`).** Novo record `LiquidationPolicy(OrderType orderType, BigDecimal maxSlippagePercent,
   int escalationTicks)` em `domain/runner`, no molde de `PositionProtection`: `defaults()` = MARKET;
   com LIMIT valida afastamento em (0, 1) e ticks ≥ 1; padrões 0.01 e 3. Campo não nulo em
   `StrategyRunner`, alterado por `updateLimits`.
2. **Persistência e API (`spring-application`).** Migração `V16__runner_liquidation_policy.sql` com
   colunas em `strategy_runners` (default MARKET); entidade e mapper. Novos campos opcionais em
   `UpdateRunnerLimitsRequest` (campo nulo mantém o valor), no mesmo endpoint de limites — sem endpoint
   novo.
3. **Execução (`core`, `SafeModeHandler`).** Em PANIC_SELL, por lote do runner:
   - política MARKET: comportamento atual, sem ramo novo;
   - política LIMIT, lote sem tentativa: SELL LIMIT do lote inteiro a `preço do tick × (1 − afastamento)`,
     pelo pipeline normal de SELL (normalização SELL→ceiling da T37, lock, persistência, dispatch);
   - LIMIT de liquidação em voo: conta um tick; ao atingir `escalationTicks`, cancela;
   - lote com tentativa LIMIT encerrada (cancelada ou parcialmente executada e cancelada): MARKET do
     restante.
   - `cancelRestingOrders` deixa de cancelar a LIMIT de liquidação em voo antes do prazo (hoje cancelaria
     a própria ordem no tick seguinte).
4. **Estado da tentativa.** Como a `Transaction` não diferencia a origem, o handler guarda em memória, por
   lote, o `clientOrderId` da LIMIT de liquidação e a contagem de ticks (mapa concorrente; runners rodam
   em mailboxes separadas). Entrada removida quando o lote fecha ou o Safe Mode sai de PANIC_SELL.
5. **Fora do escopo.** A proteção do lote (T44) e o fechamento pelo admin (T49) continuam MARKET; o tipo
   de ordem continua decidido no core (`Transaction.orderType`) e o adapter só traduz.

## Riscos

- **Restart no meio da tentativa:** o estado em memória se perde. A LIMIT em voo é cancelada pelo
  `cancelRestingOrders` no primeiro tick após o restart e o lote recebe uma nova tentativa LIMIT — no
  pior caso, mais 3 ticks antes do MARKET. Mesmo trade-off aceito na T25 para contadores em memória.
- **Sem ticks não há liquidação nem escalada**, em LIMIT ou MARKET. Comportamento já existente da T45;
  o watchdog de market data é o mecanismo para esse caso, não esta task.
- **SELL duplicada:** a escalada só ocorre depois do cancelamento confirmado (lock liberado pela
  conciliação do `CANCELED`); um fill que corra com o cancelamento é conciliado normalmente e o MARKET
  vende só o restante.

## Critérios de aceitação

1. Runner sem configuração liquida com MARKET, exatamente como na T45.
2. Runner com política LIMIT envia SELL LIMIT do lote a `preço do tick × (1 − afastamento)`, normalizado
   para cima no tick size.
3. LIMIT que preenche fecha o lote sem MARKET.
4. LIMIT sem preenchimento em 3 ticks é cancelada e, após o `CANCELED`, o restante do lote sai a MARKET;
   nunca há duas SELLs do mesmo lote em voo.
5. Fill parcial seguido de escalada vende a MARKET só a quantidade restante.
6. A LIMIT de liquidação não é cancelada pelo `cancelRestingOrders` antes do prazo.
7. Política alterável por `PUT` de limites do runner, persistida e validada (afastamento em (0, 1),
   ticks ≥ 1).

## Validação

- Unitários: `LiquidationPolicy`, `SafeModeHandler` (MARKET, LIMIT preenchida, escalada, parcial,
  não-cancelamento antecipado).
- Integração com Postgres: migração V16 e round-trip da política no repositório.
- E2E no MOCK (estende `SafeModeE2ETest`): PANIC_SELL com política LIMIT em ordem que descansa → escalada
  para MARKET e lote fechado.

## Fontes

- `.ai/tasks/T45-safe-mode-activation-and-levels.md`, `.ai/tasks/T44-strategy-independent-position-protection.md`
- `.ai/flows/portfolio-capital.md`
- `docs/OPERATIONS_RUNBOOK.md` (Safe Mode)
