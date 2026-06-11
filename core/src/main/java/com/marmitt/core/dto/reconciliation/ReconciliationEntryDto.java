package com.marmitt.core.dto.reconciliation;

import com.marmitt.core.enums.ReconciliationStatus;

public record ReconciliationEntryDto(
        ReconciliationStatus status,
        String clientOrderId,
        ExchangeSideDto exchangeSide,
        LocalSideDto localSide
) {}
