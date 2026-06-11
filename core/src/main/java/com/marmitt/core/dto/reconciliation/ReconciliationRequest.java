package com.marmitt.core.dto.reconciliation;

import java.time.Instant;

public record ReconciliationRequest(
        String symbol,
        String exchangeId,
        Instant from,
        Instant to,
        boolean includeMatched
) {}
