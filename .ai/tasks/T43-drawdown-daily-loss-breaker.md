# T43 — Circuit Breaker de Capital: Drawdown Máximo e Perda Diária

**Complexidade:** Alta (efeito financeiro + nova fonte de verdade de preço)
**Responsável:** Claude
**Dependências:** T45 (ativação do Safe Mode — o breaker precisa de um caminho para acionar `HALT`).
Relaciona-se com T25 (gatilhos automáticos de Safe Mode); ver "Relação com T25".
**Status:** Pendente

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
