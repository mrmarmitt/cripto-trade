package com.marmitt.core.ports.outbound.metrics;

/**
 * Motivo pelo qual um tick não chegou a ser avaliado pelo runner (T42, IG §12.8).
 */
public enum TickDiscardReason {
    /** Substituído na mailbox por um tick mais novo antes de ser avaliado (latest-wins). */
    COALESCED,
    /** Mais velho que o limite de idade quando a avaliação começaria (IG §12.3). */
    STALE,
    /** Timestamp anterior ao último tick aceito pelo runner. */
    OUT_OF_ORDER
}
