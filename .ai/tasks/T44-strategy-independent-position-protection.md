# T44 — Proteção de Posição Independente da Estratégia

**Complexidade:** Alta (gera ordens fora da estratégia)
**Responsável:** Claude
**Dependências:** T43 (último preço por símbolo, reutilizável). Motivada pela T41, mas vale também
para estratégias in-process.
**Status:** Concluído (2026-10-19) — ver "Entrega".

---

## Descrição

Toda saída de posição hoje depende da estratégia emitir `SHOULD_SELL`. O contrato out-of-process
define que estratégia indisponível = **HOLD implícito**: seguro para entradas, mas significa que,
com o serviço de estratégia fora do ar, **nenhuma posição aberta é vendida**, por maior que seja a
queda. O mesmo vale in-process para estratégia desabilitada, removida ou com bug que sempre retorna
HOLD.

Stop-loss aparece apenas como pergunta aberta em `docs/ROADMAP_QUESTOES.md` §7. Esta task define e
implementa uma proteção mínima que o `core` executa sem consultar a estratégia.

---

## Solução proposta

1. **Configuração por runner** (persistida, opcional, default definido na decisão de negócio):
   - `maxLossPercentPerLot` — perda máxima por lote em relação ao preço de entrada;
   - `maxHoldingDuration` — tempo máximo de vida de um lote (opcional).
2. **Avaliação no core**, a cada tick do símbolo, **antes e independente** da estratégia, dentro do
   fluxo do runner (respeitando a mailbox da T42): lote que violar um limite gera uma saída protetiva.
3. **Saída protetiva reaproveita o pipeline existente**: monta um sinal SELL com `targetLotId` do
   lote e passa pelo `SellSignalHandler` (persistência antes do envio, lock de posição, normalização).
   Nenhum caminho paralelo de ordem.
4. **Precedência:** saída protetiva vence a decisão da estratégia no mesmo tick; se o lote já estiver
   travado por outra SELL, não faz nada (o lock já garante a não-duplicação).
5. **Ordens em voo do lote:** se a estratégia tiver uma SELL limite descansando para o lote, avaliar
   cancelá-la (`SHOULD_CANCEL`/`OrderDispatchPort.cancel`, T32/T33) antes da saída protetiva.
6. **Observabilidade:** outcome `PROTECTIVE_EXIT` em `signal.evaluated.total`, log com motivo
   (`MAX_LOSS`/`MAX_HOLDING`), alerta Discord.
7. **Também com Safe Mode `HALT`:** a proteção continua ativa (ela só reduz exposição).

---

## Decisão de negócio necessária

- Defaults de `maxLossPercentPerLot` / `maxHoldingDuration` e se são obrigatórios em produção.
- Tipo de ordem da saída (MARKET vs. LIMIT agressiva com `tickSize`) e tratamento de slippage.
- Se lotes abertos antes da ativação da feature entram na proteção.

---

## Critérios de aceitação

1. Com a estratégia retornando sempre HOLD (ou indisponível), um lote que ultrapassa o limite de
   perda é vendido pelo pipeline normal, com outcome `PROTECTIVE_EXIT`.
2. Nenhuma SELL duplicada no mesmo lote (lock existente), inclusive com a estratégia emitindo SELL
   no mesmo tick.
3. A proteção opera com Safe Mode `HALT` ativo.
4. Teste E2E no harness da T38 cobrindo queda de preço com estratégia "sempre HOLD".
5. `.ai/flows/runner-signal-processing.md` atualizado.

---

## Fontes

- `docs/strategy-service/CONTRACT.md` ("Falha e timeout").
- `docs/ROADMAP_QUESTOES.md` §7 (stop-loss).
- `core/src/main/java/com/marmitt/core/application/usecase/runner/processsignal/ProcessTradeSignalUseCase.java`,
  `SellSignalHandler.java`.
- `.ai/tasks/T32-strategy-cancel-action.md`, `.ai/tasks/T33-order-cancel-dispatch-foundation.md`.

---

## Entrega (2026-10-19)

### Decisões de negócio (usuário, 2026-10-19)

- **`maxLossPercentPerLot` = 5% por padrão** para todo runner, novo e existente; ajustável por runner.
  Fica abaixo do disjuntor de drawdown de 10% do portfólio (T43). Não pode ser desligado (faixa (0, 1)).
- **`maxHoldingDuration` desligado por padrão**; o campo existe e é opcional por runner.
- **Saída a `MARKET`**, como o PANIC_SELL (T45): certeza de execução acima de preço. Proteção LIMIT
  fica junto da T48.
- **Lotes abertos antes da feature entram na proteção.**
- **Take profit fica com a estratégia:** a T44 só reduz risco.

### Decisões de implementação

- **Regra no domínio:** `PositionProtection` (value object do `StrategyRunner`) decide a violação. A
  perda é medida contra o `costBasis` do lote (T46: inclui a fee), não contra o preço médio exibido.
  Perda vence idade quando as duas valem.
- **Um lote por runner:** o runner tem no máximo um lote ativo (OPEN/CLOSING) por símbolo — compras
  somam ao lote —, então a proteção avalia `findActivePositionByRunnerIdAndSymbol`.
- **Antes da estratégia e do Safe Mode, em qualquer nível:** só reduz exposição. Quando age (venda ou
  cancelamento enviados), a estratégia não é consultada no tick. Sob `PANIC_SELL` a liquidação ignora
  o lote já travado pela saída protetiva.
- **Lote travado por SELL LIMIT da estratégia:** a proteção envia o cancelamento e vende no tick
  seguinte ao `CANCELED`. Travado por SELL MARKET (a própria saída): espera, e a estratégia segue
  (ela não consegue vender o lote travado).
- **Um caminho por saída decidida pelo core:** `TradeIntentFactory.liquidationSignal(source, ...)` +
  `liquidationOrderType()` servem PANIC_SELL, fechar posição pelo admin e a proteção (saíram do
  `SafeModeHandler`). O cancelamento de LIMIT em voo do Safe Mode e da proteção passa por
  `CancelSignalHandler.cancelResting`.
- **Configuração persistida no runner** (migration V15: `max_loss_percent_per_lot` NOT NULL default
  0.05, `max_holding_seconds` nulo) e alterada pelo `PATCH /api/admin/runners/{id}` existente
  (`maxLossPercentPerLot`, `maxHoldingDuration` ISO-8601; `PT0S` desliga a idade). O `PATCH` agora
  valida todos os campos antes de aplicar qualquer um.
- **Observabilidade:** outcome `PROTECTIVE_EXIT` (a venda despachada também conta `SELL`, como no
  Safe Mode), log WARN `protection: MAX_LOSS|MAX_HOLDING ...` e alerta `ErrorNotificationType.PROTECTIVE_EXIT`
  (Discord) quando a venda é despachada. `processTradeSignal` passou a devolver o desfecho que registra.

### Validação

- `./scripts/gradle-run.ps1 test` verde: core 215, spring-application 207, adapter-binance 76,
  adapter-mock 24, strategy 23, adapter-strategy-remote 11, strategy-service-reference 5.
- `PositionProtectionTest`: perda abaixo/no limite, custo com fee, idade desligada por padrão, perda
  vence idade, faixas inválidas.
- `PositionProtectionHandlerTest`: gatilho, saída MARKET do lote inteiro, cancelamento da SELL LIMIT
  que trava o lote, lote travado por MARKET deixado em paz.
- `StrategyRunnerInvariantsTest`: default 5% no runner novo, `PATCH` da proteção, `PT0S` desliga a
  idade, pedido inválido não aplica os outros campos.
- `PositionProtectionE2ETest` (harness da T38, MOCK): estratégia "sempre HOLD" tem o lote vendido a
  MARKET na queda (critérios 1 e 4); a proteção vende sob Safe Mode `HALT` (critério 3); estratégia
  que insiste numa SELL LIMIT tem a ordem cancelada e o lote sai numa única MARKET, sem SELL
  duplicada (critério 2). `SafeModeE2ETest` segue verde com o cancelamento unificado.

### Riscos residuais

- **Depende do feed de market data:** sem ticks do símbolo não há avaliação. Ordem stop nativa na
  exchange fica como evolução (junto da T48).
- **Slippage da MARKET** numa queda rápida: a perda realizada pode passar do limite configurado.
- **Recompra depois da saída:** a estratégia pode comprar de novo no tick seguinte; a proteção não
  impõe cooldown (o disjuntor de drawdown da T43 continua valendo para o portfólio).

