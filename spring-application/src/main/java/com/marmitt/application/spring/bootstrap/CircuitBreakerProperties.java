package com.marmitt.application.spring.bootstrap;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Configuração do circuit breaker de capital (T43). Limite global para todos os portfolios
 * (decisão do mantenedor); por portfolio fica para quando houver mais de um em operação.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "portfolio.circuit-breaker")
public class CircuitBreakerProperties {

    private boolean enabled = true;
    /** Queda máxima de equity em relação ao pico da janela de 24h, como fração (0.10 = 10%). */
    private BigDecimal maxDrawdownPercent = new BigDecimal("0.10");
    private long checkIntervalMs = 10_000L;
}
