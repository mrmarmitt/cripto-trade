package com.marmitt.core.enums;

/**
 * Motivo de rejeição de um Capital Request pelo Portfolio.
 * Populado no {@code ReservationResult} quando {@code status == REJECTED}.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 5.2.1</a>
 */
public enum RejectionReason {

    /** SafeMode ativo no Portfolio (HALT, CANCEL_ALL ou PANIC_SELL). */
    RISK_VIOLATION,

    /** A alocação do Runner excederia o limite percentual configurado. */
    RUNNER_LIMIT_EXCEEDED,

    /** Saldo disponível insuficiente para cobrir o amount solicitado. */
    INSUFFICIENT_FUNDS,

    /** Timeout na comunicação com o Portfolio. Runner trata como rejeição. */
    TIMEOUT,

    /** runnerId não encontrado no Portfolio — indica configuração inválida. */
    UNKNOWN_RUNNER
}
