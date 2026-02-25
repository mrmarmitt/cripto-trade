package com.marmitt.core.dto.capital;

import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Contexto completo do fluxo BUY para atravessar as fronteiras transacionais
 * sem depender de cache temporario em memoria.
 */
public record BuyExecutionContext(
        Transaction transaction,
        CapitalRequest capitalRequest,
        StrategyRunner runner,
        BigDecimal precomputedExposure
) {
    public BuyExecutionContext {
        Objects.requireNonNull(transaction, "transaction cannot be null");
        Objects.requireNonNull(capitalRequest, "capitalRequest cannot be null");
        Objects.requireNonNull(runner, "runner cannot be null");
    }
}
