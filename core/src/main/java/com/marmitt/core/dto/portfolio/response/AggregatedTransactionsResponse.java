package com.marmitt.core.dto.portfolio.response;

import java.util.List;
import java.util.UUID;

public record AggregatedTransactionsResponse(
        UUID portfolioId,
        String portfolioName,
        List<TransactionDto> transactions,
        int totalCount
) {
}

