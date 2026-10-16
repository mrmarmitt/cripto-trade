package com.marmitt.core.ports.inbound.portfolio;

/**
 * Avaliação periódica do drawdown de todos os portfolios ativos (T43). Acionada por scheduler.
 */
public interface EvaluateDrawdownPort {

    void evaluate();
}
