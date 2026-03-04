package com.marmitt.application.spring.infrastructure.persistence.mapper;

import com.marmitt.application.spring.infrastructure.persistence.entity.DeadLetterEntryEntity;
import com.marmitt.core.domain.portfolio.DeadLetterEntry;

public final class DeadLetterEntryEntityMapper {

    private DeadLetterEntryEntityMapper() {
    }

    public static DeadLetterEntryEntity toEntity(DeadLetterEntry domain) {
        return DeadLetterEntryEntity.builder()
                .id(domain.getId())
                .portfolioId(domain.getPortfolioId())
                .clientOrderId(domain.getClientOrderId())
                .exchangeOrderId(domain.getExchangeOrderId())
                .rawPayload(domain.getRawPayload())
                .reason(domain.getReason())
                .isResolved(domain.isResolved())
                .resolvedBy(domain.getResolvedBy())
                .resolvedAt(domain.getResolvedAt())
                .createdAt(domain.getCreatedAt())
                .build();
    }

    public static DeadLetterEntry toDomain(DeadLetterEntryEntity entity) {
        return new DeadLetterEntry(
                entity.getId(),
                entity.getPortfolioId(),
                entity.getClientOrderId(),
                entity.getExchangeOrderId(),
                entity.getRawPayload(),
                entity.getReason(),
                entity.isResolved(),
                entity.getResolvedBy(),
                entity.getResolvedAt(),
                entity.getCreatedAt()
        );
    }
}
