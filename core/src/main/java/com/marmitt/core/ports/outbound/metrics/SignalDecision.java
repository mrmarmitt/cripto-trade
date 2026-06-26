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
    REJECTED_LOCK
}
