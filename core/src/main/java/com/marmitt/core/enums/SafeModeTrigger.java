package com.marmitt.core.enums;

/**
 * Motivo de um HALT automático do portfolio (T43, T25; Blueprint 11.1.A). Gatilho automático só aciona
 * {@code HALT}; a retomada é sempre manual.
 */
public enum SafeModeTrigger {
    /** Equity caiu além do limite em relação ao pico de 24h (T43). */
    MAX_DRAWDOWN_EXCEEDED,
    /** Entradas não resolvidas na DLQ do portfolio no limite (T25). */
    DLQ_THRESHOLD,
    /** Sanidade de saldo do boot com {@code FAIL_DEFICIT}: local maior que a exchange (T25). */
    BALANCE_DIVERGENCE,
    /** Mesma transação sem resolver em ciclos consecutivos do watchdog (T25). */
    WATCHDOG_STUCK
}
