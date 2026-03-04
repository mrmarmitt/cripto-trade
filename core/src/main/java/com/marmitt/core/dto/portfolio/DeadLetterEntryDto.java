package com.marmitt.core.dto.portfolio;

import com.marmitt.core.domain.portfolio.DeadLetterEntry;
import com.marmitt.core.enums.DlqReason;

import java.time.Instant;
import java.util.UUID;

public record DeadLetterEntryDto(
        UUID id,
        UUID portfolioId,
        UUID runnerId,
        String clientOrderId,
        String exchangeOrderId,
        String rawPayload,
        DlqReason reason,
        boolean resolved,
        String resolvedBy,
        Instant resolvedAt,
        Instant createdAt
) {
    public static DeadLetterEntryDto fromDomain(DeadLetterEntry entry) {
        return new DeadLetterEntryDto(
                entry.getId(),
                entry.getPortfolioId(),
                entry.getRunnerId(),
                entry.getClientOrderId(),
                entry.getExchangeOrderId(),
                entry.getRawPayload(),
                entry.getReason(),
                entry.isResolved(),
                entry.getResolvedBy(),
                entry.getResolvedAt(),
                entry.getCreatedAt()
        );
    }
}
