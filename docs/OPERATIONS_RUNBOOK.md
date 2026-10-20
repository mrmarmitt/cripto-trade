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

## Acesso à API (T55)

Toda chamada a `/api/**` leva `X-Admin-Key: <ADMIN_API_KEY>`; sem ela a resposta é `401` com
`code=ADMIN_KEY_INVALID`. `ADMIN_API_KEY` vazio bloqueia a API inteira. `/actuator` não exige a chave.

Para começar a operar depois de um boot, as conexões e assinaturas de websocket ainda são abertas à mão
(`POST /api/websocket/connect`, `POST /api/websocket/exchange/market-data/subscribe`,
`.../order-notifications/subscribe`); o boot não as abre sozinho. Ordens só nascem pelos runners: não
existe endpoint de ordem direta na exchange.

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
| `PANIC_SELL` | Zerar exposição imediatamente | CANCEL_ALL + vende todas as posições, **a mercado** por padrão ou com LIMIT protegida conforme o runner (ver abaixo). Exige `confirm`. |

- O `PUT` responde **202** e a execução acontece nos ticks seguintes; acompanhe pelo `GET`
  (`restingOrders`, `liquidatingOrders`, `openPositions` caindo a zero).
- `idleRunners > 0`: há runners parados pelo kill switch (ou reconciliando) que o Safe Mode **não**
  conduz. Para liquidá-los, retome o runner (`POST /api/admin/runners/{id}/resume`, T49) com o
  portfolio em `PANIC_SELL`.
- Liquidação por runner (T48): por padrão o `PANIC_SELL` vende a MARKET. Para símbolos de livro raso,
  configure LIMIT no runner com `PATCH /api/admin/runners/{id}`:
  `{"liquidationOrderType": "LIMIT", "liquidationMaxSlippagePercent": 0.01, "liquidationEscalationTicks": 3}`
  (os dois últimos são o padrão). A SELL LIMIT sai a 1% abaixo do último preço; sem preenchimento em
  3 ticks é cancelada e o restante do lote sai a MARKET. Sem ticks de market data nada é liquidado,
  em nenhum dos dois tipos. Sinal no log: `safeMode: PANIC_SELL LIMIT unfilled - canceling to escalate`.
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

## Gatilhos automáticos (T25)

Além do drawdown (T43), o portfolio entra em **HALT automático** (origem `AUTOMATIC`, alerta Discord
`[CIRCUIT_BREAKER_TRIGGERED]`, `circuit_breaker_activations_total{reason}`) quando:

| Motivo | Condição | O que conferir antes de retomar |
|---|---|---|
| `DLQ_THRESHOLD` | ≥ 3 entradas não resolvidas na DLQ do portfolio | `GET /api/dead-letters`; reprocessar ou resolver as entradas |
| `BALANCE_DIVERGENCE` | Sanidade do boot com saldo local maior que o da exchange (`FAIL_DEFICIT`) | Comparar saldo da conta com `global_balances`; corrigir a origem da diferença |
| `WATCHDOG_STUCK` | Mesma transação sem resolver em 3 ciclos do watchdog (~3 min) | Ordem na exchange pelo `clientOrderId`; disponibilidade da exchange (T24) |

Runner (não o portfolio): **5 ordens rejeitadas seguidas** param só aquele runner (alerta
`[RUNNER_AUTO_HALTED]` com o último motivo de rejeição). Corrigir a causa (saldo, filtros, credencial) e
retomar com `POST /api/admin/runners/{id}/resume`.

- Retomada sempre manual. Gatilho não repete com o portfolio já em Safe Mode.
- Configuração: `safe-mode.auto.*` (cada gatilho tem `enabled`; `safe-mode.auto.enabled=false` desliga
  todos os da T25 sem afetar o Safe Mode manual nem o drawdown).

## Ciclo de vida do runner (T49)

Endpoints protegidos pelo `admin.api.key`:

| Ação | Endpoint | Efeito |
|---|---|---|
| Iniciar runner criado | `POST /api/admin/runners/{id}/start` | Reconcilia e ativa (CREATED → ACTIVE), sem restart |
| Parar | `POST /api/admin/runners/{id}/halt` | Kill switch (T8): nenhuma ordem nova |
| Retomar | `POST /api/admin/runners/{id}/resume` | Reconcilia; só volta a ACTIVE se não houver erro nem DLQ pendente |
| Encerrar | `POST /api/admin/runners/{id}/terminate` | TERMINATING: sem compras, a estratégia ainda vende; arquivado sozinho quando zerado |
| Ajustar limites | `PATCH /api/admin/runners/{id}` `{"maxAllocationPercent":0.2,"maxOpenPositions":1,"maxPendingOrders":1}` | Campo omitido mantém o valor |
| Cancelar ordem | `POST /api/admin/runners/{id}/orders/{transactionId}/cancel` | 202; o CANCELED chega pelo stream |
| Fechar posição | `POST /api/admin/runners/{id}/positions/{positionId}/close` | 202; SELL MARKET do lote inteiro |

- `409` = operação recusada pelo estado (transição inválida, exchange indisponível, reconciliação
  incompleta, posição travada por uma SELL em voo, sem preço de mercado ainda). A mensagem diz o motivo.
- Fechar posição exige runner ACTIVE ou TERMINATING; runner halted: retome antes.
- Posição travada por SELL LIMIT: cancele a ordem e depois feche a posição.

## Estado operacional e alertas (T51)

- `GET /actuator/health` → componente `safeMode`: `active` e o nível de cada portfolio. Continua `UP`
  com Safe Mode ativo (é proteção, não falha).
- Amostra a cada `ctrade.observability.report-interval-ms` (15s): `circuit_breaker_current_level`
  (0=NORMAL … 3=PANIC_SELL), `circuit_breaker_duration_active_seconds`, `capital_utilization_global`,
  `capital_utilization_runner`, `capital_reservation_oldest_age_seconds`, `runner_lifecycle_current_state`.
- Alertas do Grafana (grupo `ctrade-operational`, canal `alerts-behavior`):
  - **Safe Mode ativo** → ver a seção do Safe Mode; `GET /api/admin/portfolios/{id}/safe-mode`.
  - **Runner travado em TERMINATING** (> 10 min, log WARN `runner stuck in TERMINATING`) → conferir
    ordens em voo e posição do runner; cancelar/fechar pelos comandos da T49.
  - **Cancel rate > 10/min** → estratégia em loop de ordem/cancelamento; considerar halt do runner.
  - **Rejection rate > 50%** → ver `signal_evaluated_total` por `decision` (capital, preço, limites).
  - **Utilização global > 80%** → capital quase todo reservado; novos BUYs tendem a ser recusados.

## Circuit breaker das consultas REST (T24)

As consultas REST de ordens à Binance (boot recovery, watchdog de transações) passam por um circuit
breaker: 5 falhas de instabilidade (rede, 5xx, rate limit) abrem o circuito por 60s; erro de requisição
ou de autenticação não conta. Aberto, a consulta falha na hora como falha transitória — o boot repete
com backoff e o watchdog tenta no próximo ciclo.

- Sinais: alerta Grafana **Circuit breaker da exchange aberto**,
  `resilience4j_circuitbreaker_state{name="binance-order-query",state="open"} = 1`.
- Ação: conferir a disponibilidade da Binance (status, credenciais, rate limit). O circuito testa de
  novo sozinho na próxima consulta depois dos 60s.
- Config: `exchange.order-query.circuit-breaker.*` (`enabled: false` desliga).

## Processamento de ticks (T42)

- `runner.ticks.discarded{runnerId, reason}`: `COALESCED` cresce quando a avaliação é mais lenta que o
  feed (normal em rajadas); `STALE` indica tick que esperou mais que
  `runner.signal.stale-tick-threshold-ms` (5s) — fila de runners saturada; `OUT_OF_ORDER` deve ser raro.
- `runner.signal.processing.time{runnerId}`: duração de cada avaliação; acima de
  `runner.signal.slow-processing-warn-ms` (1s) há WARN `slow evaluation`.
- Muitos `STALE`: aumentar `runner.signal.threads` ou investigar a estratégia lenta.
- Log ERROR `marketDataExecutor: queue full`: parsing de market data não acompanha o feed.

## Proteção do lote (T44)

Cada runner vende o lote a mercado, sem consultar a estratégia, quando a perda passa de
`maxLossPercentPerLot` do custo (padrão 0.05) ou, se configurado, quando o lote passa de
`maxHoldingDuration`. Vale com a estratégia fora do ar e sob Safe Mode `HALT`.

- Sinais: log WARN `protection: MAX_LOSS ...`, alerta Discord `[PROTECTIVE_EXIT]`, outcome
  `signal.evaluated.total{decision=PROTECTIVE_EXIT}`. A venda é uma SELL `MARKET` com reasoning
  `protective exit ...`.
- Ajustar por runner: `PATCH /api/admin/runners/{id}` com `{"maxLossPercentPerLot": 0.08}` ou
  `{"maxHoldingDuration": "PT24H"}`; `"PT0S"` desliga a idade máxima. O limite de perda não desliga
  (valores em (0, 1)).
- SELL LIMIT da estratégia descansando sobre o lote é cancelada primeiro; a venda protetiva sai no
  tick seguinte ao `CANCELED`.

## Estratégia remota (T41) e catálogo de estratégias (T54)

> Com a estratégia fora do ar, desabilitada ou ainda não conferida, o runner não opera (HOLD
> implícito); a saída do lote em queda fica com a proteção do lote (T44).

A estratégia é uma app independente que implementa `docs/strategy-service/CONTRACT.md` (qualquer
linguagem; referência: `gradle :strategy-service-reference:run` com `PORT`, `STRATEGY_REF`,
`STRATEGY_TOKEN`). O catálogo fica no banco e é operado só por API, sem restart do ctrade. Todas as
chamadas levam `X-Admin-Key`; toda mudança exige `reason`; recusas trazem `code` no `ApiError`.
Contrato completo no OpenAPI (`/v3/api-docs`, tag *Strategy catalog*).

**Cadastrar e colocar em uso**

1. `POST /api/admin/strategies` com
   `{"strategyRef","name","version","baseUrl":"http://host:porta/v1","token","reason"}`.
   - Esperado: `200`, `status=ENABLED`, `verification=VERIFIED`, `usable=true`.
   - `verification=FAILED`, `usable=false`: ler `lastError` (serviço fora, nome/versão divergente ou
     contrato não suportado). O ctrade confere de novo a cada `registration-retry-interval-ms` (30s);
     corrigir a causa e acompanhar pelo `GET` até `usable=true`.
   - `409 STRATEGY_DEFINITION_CONFLICT`: o `strategyRef` já existe com outros dados. Versão nova da
     estratégia = `strategyRef` novo; endereço novo = `PATCH`.
   - Repetir o mesmo cadastro devolve o registro existente, sem efeito.
2. Criar o runner apontando para o `strategyRef` e iniciá-lo (`POST /api/admin/runners/{id}/start`).

**Verificar**

- `GET /api/admin/strategies` (ou `/{strategyRef}`): `usable`, `verification`, `lastError`,
  `lastVerificationAt` e `runners` (id, `shortCode`, símbolo, status) de cada estratégia.
- `GET /api/admin/strategies/{strategyRef}/events`: histórico (`REGISTERED`, `ENABLED`, `DISABLED`,
  `ENDPOINT_CHANGED` com `reason`; `VERIFIED`, `VERIFICATION_FAILED` com `detail`).

**Mudar endereço ou token**: `PATCH /api/admin/strategies/{strategyRef}` com `baseUrl` e/ou `token`
(nulo mantém; token vazio remove) e `reason`. A estratégia sai de uso e é conferida no endereço novo;
esperado `usable=true` na resposta.

**Tirar de uso**: `POST /api/admin/strategies/{strategyRef}/disable` com `{"reason"}`.
- Esperado: `status=DISABLED`, `usable=false`; os runners listados ficam em HOLD (nada é parado).
- Com runner `ACTIVE`, sai o alerta Discord `[STRATEGY_DISABLED]`. Para encerrar esses runners, usar
  os comandos da T49.
- Não há exclusão. Para voltar: `POST .../enable` com `{"reason"}`, que confere de novo antes do uso.

Sinais: `signal.evaluated.total{decision=STRATEGY_UNAVAILABLE}` (estratégia não decidiu),
`strategy.remote.failures{reason=UNREACHABLE|ERROR_RESPONSE|INVALID_RESPONSE|STALE_DECISION}` e
`strategy.remote.latency` por `strategyRef`. Parâmetros: `timeout-ms` (250), `max-decision-age-ms` (2000).
