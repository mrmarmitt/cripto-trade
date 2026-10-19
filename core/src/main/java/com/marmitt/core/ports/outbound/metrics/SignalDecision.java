package com.marmitt.core.ports.outbound.metrics;

/**
 * Classificacao mutuamente exclusiva do desfecho de uma avaliacao de sinal,
 * usada como tag da metrica {@code signal.evaluated.total} (T23 G2).
 *
 * <p>Representa o <em>outcome</em> efetivo do tick para o runner, nao a decisao bruta
 * da estrategia: um BUY decidido pela estrategia mas recusado na reserva de capital
 * conta como {@link #REJECTED_CAPITAL}, nunca como {@link #BUY}.
 */
public enum SignalDecision {
    HOLD,
    BUY,
    SELL,
    CANCEL,
    REJECTED_CAPITAL,
    REJECTED_LOCK,
    REJECTED_NO_POSITION,
    REJECTED_POLICY,
    /** Tick conduzido pelo Safe Mode (CANCEL_ALL/PANIC_SELL): a estrategia nao foi consultada (T45). */
    SAFE_MODE,
    /** A estrategia nao produziu decisao utilizavel (falha, indisponivel, resposta invalida, velha) — T41. */
    STRATEGY_UNAVAILABLE,
    /**
     * A protecao do lote agiu no tick (venda MARKET ou cancelamento da SELL LIMIT que travava o lote) e
     * a estrategia nao foi consultada — T44. A venda em si tambem conta como {@link #SELL}.
     */
    PROTECTIVE_EXIT
}
