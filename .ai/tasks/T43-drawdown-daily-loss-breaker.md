# T43 — Circuit Breaker de Capital: Drawdown Máximo e Perda Diária

**Complexidade:** Alta (efeito financeiro + nova fonte de verdade de preço)
**Responsável:** Claude
**Dependências:** T45 (concluída — acionar `HALT` via `ManageSafeModePort.change` com `SafeModeOrigin.AUTOMATIC`).
Relaciona-se com T25 (gatilhos automáticos de Safe Mode); ver "Relação com T25".
**Status:** Concluído (2026-10-16) — ver "Decisões registradas" e "Entrega".

---

## Descrição

Não existe nenhum limite baseado em perda: em 2026-10-15, buscas por `drawdown`, `dailyLoss` e
`stopLoss` em `src/main` retornam zero ocorrências. Os únicos freios são o kill switch manual (T8) e o
Safe Mode, que hoje nem tem caminho de ativação (T45).

O design já decidiu o comportamento — BLUEPRINT §11.1.A (gatilho 1, Max Daily Drawdown) e IG ~l.3379
(`DrawdownMonitor`, métricas `circuit_breaker.drawdown.*`, configs
`portfolio.circuit-breaker.max-drawdown-percent = 0.10` e `drawdown-check-interval-ms = 10000`, sem
retomada automática) —, mas nada foi implementado. Esta task implementa esse desenho.

---

## Pré-requisito: marcação a mercado

O drawdown é sobre `GlobalBalance` **realizado + flutuante**. Hoje o flutuante não existe:
`Position.updateCurrentPrice` não tem chamador, então `currentPrice` fica igual ao `averagePrice` da
abertura e `getUnrealizedPnl()` é sempre zero.

Parte desta task: manter um **último preço por símbolo/exchange** (alimentado pelo market data,
fora do caminho transacional) e calcular o valor de mercado das posições abertas a partir dele, sem
gravar a cada tick. Decidir na implementação entre um cache em memória exposto por port no core e a
atualização periódica de `positions.current_price`.

---

## Solução proposta

1. **Equity:** `equity = availableBalance + reservedBalance + valor de mercado das posições abertas`
   (custo reservado de ordens em voo entra pelo `reserved`; confirmar contra a reserva da T36).
2. **Monitores** (por portfolio, periódicos, no core, acionados por scheduler do spring):
   - **Max drawdown 24h:** `(peakEquity24h − equity) / peakEquity24h > maxDrawdownPercent`.
   - **Perda diária:** `equityInícioDoDia(UTC) − equity > maxDailyLoss` (absoluto e/ou percentual).
3. **Ação:** aciona Safe Mode `HALT` via o caminho da T45, com motivo `MAX_DRAWDOWN_EXCEEDED` /
   `DAILY_LOSS_EXCEEDED`, alerta Discord (T21) e trilha de auditoria.
4. **Sem retomada automática** (BLUEPRINT §11.1.C): exige reset manual com motivo.
5. **Estado persistido:** pico de 24h e equity de abertura do dia sobrevivem a restart.
6. **Métricas:** `circuit_breaker.drawdown.current`, `circuit_breaker.drawdown.peak_24h`,
   `circuit_breaker.daily_loss.current`; alerta de WARNING a 50% do limite (IG).

### Relação com T25

A T25 propõe o gatilho "capital disponível < X% do inicial" como proteção contra drawdown. Ele não
substitui este breaker (ignora o flutuante e a janela de 24h). Ao implementar, unificar o caminho de
ativação (`SafeModeActivationPort`/motivos) entre T25 e T43 para não haver dois mecanismos paralelos.

---

## Decisões registradas (2026-10-16)

- **Limite (mantenedor):** drawdown de **10%** sobre o pico da janela de 24h, como no IG.
- **Só o drawdown 24h (mantenedor):** a perda diária desde a meia-noite UTC ficou fora: sobrepõe-se
  quase sempre ao drawdown e criaria um segundo monitor com outra âncora. Pode virar task se fizer falta.
- **Escopo global (mantenedor):** `portfolio.circuit-breaker.max-drawdown-percent` vale para todos os
  portfolios; limite por portfolio fica para quando houver mais de um em operação.
- **Equity = `available + reserved + PnL flutuante` (implementação).** O realizado já está dentro de
  `available` (`confirmExecution`), então somá-lo de novo — como na fórmula original do IG §11.1.2 —
  contaria duas vezes (IG corrigido). O custo dos lotes abertos e das ordens em voo está em `reserved`
  (T36); o flutuante é `preço × quantidade − cost_basis` (`Position.getUnrealizedPnl`, fonte única).
- **Último preço em memória (implementação):** `LatestPricePort`, escrito pelo listener de market data
  (que já mantinha um cache próprio só para log — agora é o único). Não persiste: após restart o
  monitor espera os primeiros ticks. Gravar `positions.current_price` a cada tick foi descartado.
- **Equity incompleto não é avaliado:** se alguma posição ativa não tem preço, o portfolio é pulado
  naquela rodada (log WARN), para não gravar pico ou vale falso.
- **Janela reinicia no reset manual:** o pico considera amostras desde `agora − 24h` ou desde a última
  descida manual para NORMAL, o que for mais recente. Sem isso, o pico antigo reacionaria o HALT logo
  após a retomada.
- **Amostras persistidas** em `equity_snapshots` a cada avaliação (10s); amostras com mais de 24h são
  removidas pelo próprio monitor. O pico sobrevive a restart.
- **Alertas:** Discord só na ativação (`CIRCUIT_BREAKER_TRIGGERED`); a 50% do limite, log WARN e gauge,
  para não gerar ruído no canal.
- **Mesmo cálculo na consulta de PnL:** `QueryPortfolioUseCase` passa a marcar a mercado pelo mesmo
  colaborador (`PortfolioMarkToMarket`) e a considerar lotes travados (CLOSING), antes ignorados.

## Entrega (2026-10-16)

| Camada | Mudança |
|---|---|
| core | `EvaluateDrawdownPort`/`EvaluateDrawdownUseCase` (aciona HALT via `ManageSafeModePort` com `AUTOMATIC`); `PortfolioMarkToMarket`; `LatestPricePort`; `EquitySnapshotRepositoryPort`; `CircuitBreakerMetricsPort`; `StrategyRunnerRepositoryPort.findActivePositionsByPortfolioId`; `Position.getUnrealizedPnl` pelo `cost_basis`; contexto da estratégia marcado ao preço do tick (`RunnerContextAssembler`) |
| spring | `DrawdownMonitorScheduler` + `CircuitBreakerProperties` (`portfolio.circuit-breaker.*`); `InMemoryLatestPriceRepository`; `JdbcEquitySnapshotRepositoryAdapter`; `MicrometerCircuitBreakerMetricsAdapter` (`circuit_breaker.drawdown.current`, `.peak_24h`, `circuit_breaker.equity.current`, `circuit_breaker.activations.total`); migration `V14__create_equity_snapshots.sql` |

### Validação

- `:core:test` 175 — `EvaluateDrawdownUseCaseTest`: perda realizada acima do limite aciona HALT com
  motivo, métrica e Discord; perda dentro do limite não aciona; perda flutuante pelo último preço
  aciona; posição sem preço pula a avaliação; pico com mais de 24h sai da janela; pico sobrevive a
  restart; reset manual abre nova janela; portfolio já em Safe Mode é medido mas não reacionado;
  amostras antigas são removidas. `PositionCostBasisTest`: flutuante pelo custo pago.
- `:spring-application:test` 189 — `DrawdownMonitorIntegrationTest` (Postgres): HALT automático com
  auditoria em `safe_mode_changes`; reset manual não reaciona; lote travado (CLOSING) entra no equity.

### Riscos residuais

- **Preço parado:** se os ticks de um símbolo param, o último preço fica velho e o flutuante deixa de
  acompanhar o mercado. A queda de conexão de mercado já é tratada pelo watchdog de conexão.
- **Após restart**, portfolio com posição aberta só é avaliado depois do primeiro tick de cada símbolo.
- **Preço por símbolo**, independentemente da exchange de origem do tick.
- **Volume de amostras:** ~8.640 linhas/dia por portfolio (removidas após 24h).

## Critérios de aceitação

1. Posições abertas são marcadas a mercado a partir do último preço conhecido; `unrealizedPnl`
   deixa de ser constante zero (o `StrategyContextDto` também passa a refletir isso).
2. Queda de equity acima do limite de drawdown 24h ou de perda diária coloca o portfolio em `HALT`
   automaticamente, com motivo, alerta e auditoria.
3. Nenhuma retomada automática; reset manual registrado.
4. Pico/âncora diária persistem após restart.
5. Testes cobrindo: limite atingido por perda realizada, por perda flutuante, janela de 24h rolando,
   virada de dia UTC, restart no meio da janela.

---

## Fontes

- `docs/BLUEPRINT.md` §11.1.A–C; `docs/IMPLEMENTATION_GUIDE.md` ~l.3379–3420 e ~l.3790–3820.
- `core/src/main/java/com/marmitt/core/domain/portfolio/GlobalBalance.java`.
- `core/src/main/java/com/marmitt/core/domain/runner/Position.java` (`updateCurrentPrice`, `getUnrealizedPnl`).
- `.ai/tasks/T25-safe-mode-automatico.md`, `.ai/tasks/T45-safe-mode-activation-and-levels.md`.
