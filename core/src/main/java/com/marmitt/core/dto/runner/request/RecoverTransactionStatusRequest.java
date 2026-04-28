package com.marmitt.core.dto.runner.request;

import java.util.Objects;
import java.util.UUID;

public record RecoverTransactionStatusRequest(UUID transactionId) {

    public RecoverTransactionStatusRequest {
        Objects.requireNonNull(transactionId, "transactionId cannot be null");
    }
}
