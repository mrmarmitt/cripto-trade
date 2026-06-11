package com.marmitt.core.dto.reconciliation;

import java.time.Instant;

public record ReconciliationRequest(
        String symbol,
        Instant from,
        Instant to,
        boolean includeMatched
) {}
