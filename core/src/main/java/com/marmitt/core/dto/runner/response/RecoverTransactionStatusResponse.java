package com.marmitt.core.dto.runner.response;

import com.marmitt.core.enums.TransactionStatus;

import java.util.Objects;
import java.util.UUID;

public record RecoverTransactionStatusResponse(
        UUID transactionId,
        UUID runnerId,
        String exchangeId,
        TransactionStatus statusBefore,
        TransactionStatus statusAfter,
        RecoveryOutcome outcome,
        RecoveryAction action,
        FailureReason failureReason,
        String message
) {

    public RecoverTransactionStatusResponse {
        Objects.requireNonNull(transactionId, "transactionId cannot be null");
        Objects.requireNonNull(outcome, "outcome cannot be null");
        Objects.requireNonNull(action, "action cannot be null");
    }

    public static RecoverTransactionStatusResponse recovered(UUID transactionId,
                                                             UUID runnerId,
                                                             String exchangeId,
                                                             TransactionStatus statusBefore,
                                                             TransactionStatus statusAfter,
                                                             RecoveryAction action,
                                                             String message) {
        return new RecoverTransactionStatusResponse(
                transactionId,
                runnerId,
                exchangeId,
                statusBefore,
                statusAfter,
                RecoveryOutcome.RECOVERED,
                action,
                null,
                message
        );
    }

    public static RecoverTransactionStatusResponse skipped(UUID transactionId,
                                                           UUID runnerId,
                                                           String exchangeId,
                                                           TransactionStatus statusBefore,
                                                           String message) {
        return new RecoverTransactionStatusResponse(
                transactionId,
                runnerId,
                exchangeId,
                statusBefore,
                statusBefore,
                RecoveryOutcome.SKIPPED,
                RecoveryAction.NONE,
                FailureReason.TRANSACTION_NOT_ELIGIBLE,
                message
        );
    }

    public static RecoverTransactionStatusResponse failed(UUID transactionId,
                                                          UUID runnerId,
                                                          String exchangeId,
                                                          TransactionStatus statusBefore,
                                                          FailureReason failureReason,
                                                          String message) {
        return new RecoverTransactionStatusResponse(
                transactionId,
                runnerId,
                exchangeId,
                statusBefore,
                statusBefore,
                RecoveryOutcome.FAILED,
                RecoveryAction.NONE,
                Objects.requireNonNull(failureReason, "failureReason cannot be null"),
                message
        );
    }

    public enum RecoveryOutcome {
        RECOVERED,
        SKIPPED,
        FAILED
    }

    public enum RecoveryAction {
        NONE,
        RECONCILED_FROM_EXCHANGE,
        MARKED_EXPIRED,
        MARKED_CANCELED
    }

    public enum FailureReason {
        TRANSACTION_NOT_FOUND,
        TRANSACTION_NOT_ELIGIBLE,
        RUNNER_NOT_FOUND,
        ORDER_QUERY_NOT_AVAILABLE,
        ORDER_QUERY_UNSUPPORTED,
        EXCHANGE_QUERY_RETRYABLE_FAILURE,
        EXCHANGE_QUERY_TERMINAL_FAILURE,
        INVALID_EXCHANGE_RESPONSE
    }
}
