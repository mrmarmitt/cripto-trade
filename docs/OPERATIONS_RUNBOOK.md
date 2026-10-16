# Operations Runbook — CTrade

## Propósito

Este documento define **como operar, monitorar e manter** o sistema em produção. É o guia de referência para quem precisa entender o comportamento do sistema em runtime, responder a incidentes e garantir a saúde operacional.

## Escopo

- SLA e requisitos não-funcionais: latência máxima, throughput por Runner, tempo de reconciliação
- Audit Trail: eventos a serem logados, retenção, rastreabilidade de decisões e transições de estado
- Security Boundaries: isolamento entre Runners, limites de recursos, proteção de conexões
- Testing Strategy: mocks entre agregados, testes de integração, simulação de falhas, testes de carga
- Gestão de WebSocket: conexões compartilhadas vs por Runner, rate limits, reconexão

## Relação com outros documentos

- **[Blueprint](BLUEPRINT.md):** Define a arquitetura que este runbook monitora
- **Implementation Guide:** Fornece as decisões técnicas que impactam o comportamento operacional

## Pré-requisitos de conta na exchange

### Binance: pagamento de taxas com BNB desligado (T46)

A opção **"Usar BNB para pagar taxas"** deve ficar **desligada** na conta (testnet e produção). O
sistema contabiliza a comissão cobrada no ativo recebido (base na compra, cotação na venda) e não
converte fee de terceiro ativo. Se a opção for ligada, cada fill chega com fee em BNB: o sistema
segue operando, mas a fee fica fora do PnL e é registrada em log `ERROR` com
`unsupported BUY fee asset` / `unsupported SELL fee asset` — sinal para desligar a opção.

## Safe Mode do portfolio (T45)

Endpoints protegidos pelo `admin.api.key` (header da T8):

```
GET /api/admin/portfolios/{id}/safe-mode
PUT /api/admin/portfolios/{id}/safe-mode
    {"level": "HALT" | "CANCEL_ALL" | "PANIC_SELL" | "NORMAL", "reason": "...", "confirm": true|false}
```

| Nível | Quando usar | Efeito |
|---|---|---|
| `HALT` | Suspeita de problema; parar de comprar | Nenhum BUY novo. A estratégia segue e pode vender. |
| `CANCEL_ALL` | Tirar todas as ordens LIMIT do livro | HALT + cancela ordens LIMIT em voo. Estratégia parada. Exige `confirm`. |
| `PANIC_SELL` | Zerar exposição imediatamente | CANCEL_ALL + vende **a mercado** todas as posições. Exige `confirm`. |

- O `PUT` responde **202** e a execução acontece nos ticks seguintes; acompanhe pelo `GET`
  (`restingOrders`, `liquidatingOrders`, `openPositions` caindo a zero).
- `idleRunners > 0`: há runners parados pelo kill switch (ou reconciliando) que o Safe Mode **não**
  conduz. **Ainda não há API para reativar um runner halted** (T49); até lá, as posições desses
  runners precisam ser tratadas manualmente na exchange.
- Retomada: desça **um nível por vez** (`PANIC_SELL` → `CANCEL_ALL` → `HALT` → `NORMAL`), cada passo com motivo.
- `409`: transição recusada (pulo na descida, falta de `confirm`, motivo vazio ou alteração concorrente).
- Toda mudança fica em `safe_mode_changes` (de/para, origem, motivo, data).

## Circuit breaker de drawdown (T43)

- O monitor roda a cada 10s (`portfolio.circuit-breaker.check-interval-ms`). Equity =
  `available + reserved + PnL flutuante` das posições ao último preço.
- Queda acima de **10%** (`max-drawdown-percent`) em relação ao pico das últimas 24h coloca o
  portfolio em **HALT automático** (motivo `MAX_DRAWDOWN_EXCEEDED`), com aviso no Discord e registro
  em `safe_mode_changes` (origem `AUTOMATIC`). A 50% do limite há log WARN.
- **Não há retomada automática.** Depois de analisar a perda, desça para `NORMAL` pela API do Safe
  Mode; o pico é recalculado a partir do reset, então o breaker não reaciona pelo pico antigo.
- Gauges: `circuit_breaker.drawdown.current`, `circuit_breaker.drawdown.peak_24h`,
  `circuit_breaker.equity.current` (tag `portfolioId`); contador `circuit_breaker.activations.total`.
- Log `drawdown: no market price ... skipped` logo após subir é esperado até o primeiro tick do
  símbolo; se persistir, o símbolo da posição não está recebendo market data.
- Para desligar (ex.: teste controlado): `portfolio.circuit-breaker.enabled=false`.
