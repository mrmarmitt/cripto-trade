package com.marmitt.core.ports.outbound.metrics;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Métricas do monitor de drawdown (T43, IG §11.7).
 */
public interface CircuitBreakerMetricsPort {

    /** Estado do portfolio na última avaliação: equity, pico da janela e drawdown (fração). */
    void recordDrawdown(UUID portfolioId, BigDecimal equity, BigDecimal peak, BigDecimal drawdown);

    /** Ativação automática do Safe Mode pelo monitor. */
    void recordActivation(UUID portfolioId, String reason);
}
