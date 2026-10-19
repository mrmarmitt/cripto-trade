# T51 — Observabilidade de Capital, Safe Mode e Lifecycle

**Complexidade:** Baixa/Média
**Responsável:** Claude
**Dependências:** T49 (métricas de lifecycle dependem das transições por API).
**Status:** Concluído (2026-10-19) — ver "Entrega".

---

## Descrição

Achado da T39: métricas e sinais operacionais descritos no IG que não existem.

| Origem | Item |
|---|---|
| IG §7.7 | Rejection rate por runner, utilização de capital (runner e global), duração média da reserva |
| IG §7.4.1 | Cancel rate por runner (soft limit: alerta, sem bloqueio) |
| IG §11.7 | `circuit_breaker.current_level`, `circuit_breaker.duration.active`, `circuit_breaker.runner.suspended.count` |
| IG §11.4.3 | `/actuator/health` com o nível do Safe Mode |
| IG §13.7 | Transições de lifecycle, runners em TERMINATING além do timeout |

## Solução proposta

Ports de métrica no core (padrão `SignalMetricsPort`/`CircuitBreakerMetricsPort`), implementação
Micrometer no spring, `HealthIndicator` lendo o nível pelo `ManageSafeModePort`. Sem regra de negócio
nova: só exposição do estado.

## Critérios de aceitação

1. Gauges/contadores listados expostos no Prometheus com tags por runner/portfolio.
2. Health mostra o nível do Safe Mode por portfolio.

---

## Entrega (2026-10-19)

### Decisões de implementação

- **Amostra no core, exposição no spring:** `ReportOperationalStateUseCase` (core) lê portfolios,
  saldos e runners e publica em `OperationalMetricsPort`; `OperationalStateScheduler` agenda a cada
  `ctrade.observability.report-interval-ms` (15s); `MicrometerOperationalMetricsAdapter` guarda os
  gauges. Sem regra nova: as contas são as que já decidem em outro lugar.
- **Utilização do runner = mesma conta da reserva:** exposição em voo ÷ (`maxAllocationPercent` ×
  total), pela fonte única `RunnerExposureService.inFlightSummary` (a reserva usa a mesma).
- **Duração da reserva como idade da mais antiga:** `capital.reservation.oldest.age.seconds` no lugar
  da média do IG — detecta ordem travada sem instrumentar a conciliação. Sem alerta: ordem LIMIT que
  descansa é legítima.
- **Rejection rate e cancel rate em PromQL** sobre `signal.evaluated.total` (já tem os desfechos por
  runner), sem contador novo. O soft limit de cancelamento (IG §7.4.1, 10/min) virou alerta Grafana.
- **Transições explícitas:** os quatro use cases que mudam o status do runner (criação, lifecycle por
  API, kill switch, recovery do boot) chamam `recordTransition`; o arquivamento registra o tempo em
  TERMINATING. Runner além do timeout (10 min, IG §13.8) gera WARN e conta em `termination.stuck`.
- **Health UP com Safe Mode ativo:** componente `safeMode` com o nível por portfolio
  (`show-details: always`). DOWN faria um orquestrador reiniciar o processo no meio da liquidação.
- **Fora:** `circuit_breaker.cancel_all.progress`/`panic_sell.progress` (o `GET .../safe-mode` já
  mostra ordens e posições restantes), `runner.lifecycle.health_check.failures` (não há health check
  de lifecycle).

### Validação

- `./scripts/gradle-run.ps1 test` verde (core 224, spring-application 211; demais inalterados).
- `ReportOperationalStateUseCaseTest`: nível/tempo do Safe Mode, utilização global, runners HALTED,
  utilização do runner e reserva mais antiga, contagem por status e TERMINATING travado.
- `MicrometerOperationalMetricsAdapterTest`: gauges mantêm a última amostra, contador de transições,
  timer de TERMINATING e health com o nível por portfolio (UP).
- `ManageRunnerLifecycleUseCaseTest`: terminate → archive registra as transições e a duração.
- `rules.yml` validado como YAML (5 regras novas no grupo `ctrade-operational`).

