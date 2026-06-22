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

    /**
     * Recovery de runtime: a politica de not-found e resolvida pelo engine a partir do
     * status <b>recarregado</b> da transacao (evita corrida com USER_DATA entre a selecao
     * do lote e o execute) — {@code PENDING} (nunca confirmado) vira terminal fallback;
     * {@code SUBMITTED}/{@code PARTIAL} (confirmado) vira DLQ.
     */
    public static RecoverTransactionStatusRequest forRuntimeWatchdog(UUID transactionId) {
        return new RecoverTransactionStatusRequest(transactionId, MissingOrderPolicy.DERIVE_FROM_STATUS);
    }

    public enum MissingOrderPolicy {
        APPLY_TERMINAL_FALLBACK,
        REGISTER_DLQ,
        /** Resolve no engine pelo status recarregado: PENDING -> terminal fallback; senao -> DLQ. */
        DERIVE_FROM_STATUS
    }
}
