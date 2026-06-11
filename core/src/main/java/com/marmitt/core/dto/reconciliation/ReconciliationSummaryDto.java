package com.marmitt.core.dto.reconciliation;

public record ReconciliationSummaryDto(
        int total,
        int matched,
        int divergent,
        int exchangeOnly,
        int localOnly
) {}
