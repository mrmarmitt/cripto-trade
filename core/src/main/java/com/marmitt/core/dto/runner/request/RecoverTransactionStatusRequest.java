package com.marmitt.core.dto.runner.request;

import java.util.Objects;
import java.util.UUID;

public record RecoverTransactionStatusRequest(
        UUID transactionId,
        MissingOrderPolicy missingOrderPolicy
) {

    public RecoverTransactionStatusRequest {
        Objects.requireNonNull(transactionId, "transactionId cannot be null");
        Objects.requireNonNull(missingOrderPolicy, "missingOrderPolicy cannot be null");
    }

    public RecoverTransactionStatusRequest(UUID transactionId) {
        this(transactionId, MissingOrderPolicy.REGISTER_DLQ);
    }

    public static RecoverTransactionStatusRequest forBoot(UUID transactionId) {
        return new RecoverTransactionStatusRequest(transactionId, MissingOrderPolicy.APPLY_TERMINAL_FALLBACK);
    }

    public static RecoverTransactionStatusRequest forRuntimeWatchdog(UUID transactionId) {
        return new RecoverTransactionStatusRequest(transactionId, MissingOrderPolicy.REGISTER_DLQ);
    }

    /**
     * Limpeza de reserva orfa (zombie) em runtime: PENDING nunca confirmado pela exchange.
     * Quando a exchange nao conhece a ordem, o desfecho e terminacao local (EXPIRED/CANCELED),
     * nunca DLQ — um zombie nunca-enviado nao e conflito de reconciliacao.
     */
    public static RecoverTransactionStatusRequest forRuntimeOrphanCleanup(UUID transactionId) {
        return new RecoverTransactionStatusRequest(transactionId, MissingOrderPolicy.APPLY_TERMINAL_FALLBACK);
    }

    public enum MissingOrderPolicy {
        APPLY_TERMINAL_FALLBACK,
        REGISTER_DLQ
    }
}
