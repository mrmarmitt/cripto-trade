# T24 — Circuit Breaker nas Chamadas REST à Exchange

**Complexidade:** Média  
**Responsável:** Claude  
**Dependências:** Nenhuma  
**Status:** Pendente

---

## Descrição

O sistema faz chamadas REST à exchange em dois contextos principais: durante o boot recovery (query de status de ordens) e no watchdog de transações stale (`RunnerTransactionRecoveryWatchdog`, ciclo a cada 60s). Se a exchange estiver instável, essas chamadas falham repetidamente — sem nenhuma proteção, o sistema continua tentando, acumula logs de erro, pode atingir rate limit da exchange e ainda mascara o problema real.

Um circuit breaker interrompe as tentativas após N falhas consecutivas, aguarda um período de recuperação e testa novamente de forma controlada — protegendo tanto o sistema local quanto a exchange.

---

## Contexto técnico

### Pontos de chamada REST hoje

| Componente | Chamada | Contexto |
|---|---|---|
| `RunnerBootRecoveryUseCase` | `queryOrderByClientOrderIdWithRetry()` via `ExchangeOrderQueryPort` | Boot recovery por runner |
| `RecoverTransactionStatusUseCase` | `orderQuery.queryOrderByClientOrderId()` | Watchdog de stale transactions (runtime) |

Ambos usam `ExchangeOrderQueryPort` → `BinanceOrderQueryAdapter` (ou equivalente).

### Tratamento de falha atual

- `RunnerBootRecoveryUseCase`: retry com backoff exponencial configurável (`exchangeQueryMaxAttempts`, `exchangeQueryInitialBackoffMs`, etc.) — **sem circuit breaker**
- `RecoverTransactionStatusUseCase`: falhas transitórias (`ExchangeQueryException.isRetryable()`) retornam `FAILED` e o watchdog tenta novamente no próximo ciclo — **sem circuit breaker**

---

## Solução proposta

Adicionar **Resilience4j CircuitBreaker** por exchange nas chamadas via `ExchangeOrderQueryPort`.

### Biblioteca

Resilience4j já é padrão no ecossistema Spring Boot 3.x. Adicionar:

```groovy
// spring-application/build.gradle
implementation 'io.github.resilience4j:resilience4j-spring-boot3'
implementation 'io.github.resilience4j:resilience4j-circuitbreaker'
```

### Configuração (application.yml)

```yaml
resilience4j:
  circuitbreaker:
    instances:
      exchange-order-query:
        slidingWindowSize: 10
        failureRateThreshold: 50          # abre após 50% de falhas na janela
        waitDurationInOpenState: 60s      # aguarda 60s antes de tentar novamente
        permittedNumberOfCallsInHalfOpenState: 3
        recordExceptions:
          - com.marmitt.core.exceptions.ExchangeQueryException
        ignoreExceptions:
          - com.marmitt.core.exceptions.ExchangeQueryException  # apenas as não-retryáveis
```

### Aplicação

Duas opções — avaliar qual se encaixa melhor na arquitetura:

**Opção A — No adapter de infraestrutura** (Spring application layer):  
Envolver `BinanceOrderQueryAdapter.queryOrderByClientOrderId()` com `@CircuitBreaker(name = "exchange-order-query")`. Mantém o core limpo.

**Opção B — Via port wrapper no Spring config**:  
Criar um `CircuitBreakerExchangeOrderQueryAdapter` que decora o adapter real. Mais explícito, sem anotação no adapter.

### Comportamento quando circuito abre

- Boot recovery: `ExchangeQueryException` com tipo `TEMPORARY` → retry logic existente trata como falha transitória → runner vai para DLQ operacional após esgotar tentativas
- Watchdog: `RecoverTransactionStatusUseCase` retorna `FAILED` → transação volta para o próximo ciclo → quando circuito fechar, watchdog retoma normalmente

### Observabilidade

Resilience4j expõe métricas automáticas via Micrometer:
- `resilience4j_circuitbreaker_state{name="exchange-order-query"}` — estado atual (CLOSED/OPEN/HALF_OPEN)
- `resilience4j_circuitbreaker_calls_total{outcome}` — contagem de chamadas por resultado

Adicionar painel no Grafana e alert rule: `circuitbreaker_state == OPEN` → `#alerts-behavior`.

---

## Arquivos a modificar

| Arquivo | Mudança |
|---|---|
| `spring-application/build.gradle` | Adicionar dependência Resilience4j |
| `spring-application/src/main/resources/application.yml` | Configuração do circuit breaker |
| `adapter-binance/.../BinanceOrderQueryAdapter.java` (ou wrapper) | Aplicar `@CircuitBreaker` ou criar decorator |
| `docker/grafana/provisioning/dashboards/ctrade.json` | Painel de estado do circuit breaker |

---

## Critérios de aceitação

1. Após 5 falhas consecutivas na query REST, o circuit breaker abre e as chamadas subsequentes falham rápido (sem aguardar timeout HTTP).
2. Após `waitDurationInOpenState`, o circuit breaker testa novamente com `permittedNumberOfCallsInHalfOpenState` chamadas.
3. Métrica `resilience4j_circuitbreaker_state` visível no Prometheus.
4. Boot recovery e watchdog continuam funcionando corretamente quando o circuito está fechado.
5. Nenhuma mudança de comportamento funcional — só proteção contra cascata de falhas.
