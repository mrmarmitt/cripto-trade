package com.marmitt.core.dto.portfolio;

import com.marmitt.core.enums.DlqReason;

public record PortfolioZombieCandidate(
        String clientOrderId,
        String exchangeOrderId,
        String symbol,
        DlqReason reason,
        String code
) {
}
