package com.marmitt.application.spring.config.exchange;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Circuit breaker das consultas REST de ordens à exchange (T24). Conta só falhas de instabilidade
 * ({@code ExchangeQueryException} retryable: rede, 5xx, rate limit); erro de requisição ou de
 * autenticação não abre o circuito.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "exchange.order-query.circuit-breaker")
public class ExchangeCircuitBreakerProperties {

    private boolean enabled = true;
    /** Janela por contagem das últimas chamadas. */
    private int slidingWindowSize = 10;
    /** Chamadas mínimas na janela antes de avaliar a taxa: 5 falhas seguidas já abrem. */
    private int minimumNumberOfCalls = 5;
    /** Percentual de falhas na janela que abre o circuito. */
    private float failureRateThreshold = 50f;
    /** Tempo aberto antes de testar de novo. */
    private long waitDurationInOpenStateMs = 60_000L;
    /** Chamadas de teste no meio-aberto. */
    private int permittedNumberOfCallsInHalfOpenState = 3;
}
