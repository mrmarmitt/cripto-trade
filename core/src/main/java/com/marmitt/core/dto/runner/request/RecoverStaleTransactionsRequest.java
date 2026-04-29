package com.marmitt.core.dto.runner.request;

import java.time.Instant;
import java.util.Objects;

public record RecoverStaleTransactionsRequest(
        Instant updatedBefore,
        int maxPerRun
) {

    public RecoverStaleTransactionsRequest {
        Objects.requireNonNull(updatedBefore, "updatedBefore cannot be null");
        if (maxPerRun < 0) {
            throw new IllegalArgumentException("maxPerRun cannot be negative");
        }
    }
}
