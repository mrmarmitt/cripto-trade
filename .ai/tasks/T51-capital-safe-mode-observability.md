# T51 — Observabilidade de Capital, Safe Mode e Lifecycle

**Complexidade:** Baixa/Média
**Responsável:** Claude
**Dependências:** T49 (métricas de lifecycle dependem das transições por API).
**Status:** Pendente

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
