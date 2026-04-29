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

    public enum MissingOrderPolicy {
        APPLY_TERMINAL_FALLBACK,
        REGISTER_DLQ
    }
}
