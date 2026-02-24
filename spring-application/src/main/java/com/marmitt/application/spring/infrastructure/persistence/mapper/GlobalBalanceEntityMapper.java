package com.marmitt.application.spring.infrastructure.persistence.mapper;

import com.marmitt.application.spring.infrastructure.persistence.entity.GlobalBalanceEntity;
import com.marmitt.core.domain.portfolio.GlobalBalance;

public class GlobalBalanceEntityMapper {

    // ==================== Domain → Entity ====================

    public static GlobalBalanceEntity toEntity(GlobalBalance domain) {
        return GlobalBalanceEntity.builder()
                .portfolioId(domain.getPortfolioId())
                .availableBalance(domain.getAvailableBalance())
                .reservedBalance(domain.getReservedBalance())
                .realizedBalance(domain.getRealizedBalance())
                .initialCapital(domain.getInitialCapital())
                .baseCurrency(domain.getBaseCurrency())
                .totalFeesPaid(domain.getTotalFeesPaid())
                .lastExecutionTime(domain.getLastExecutionTime())
                .updatedAt(domain.getUpdatedAt())
                .version(domain.getVersion())
                .build();
    }

    // ==================== Entity → Domain ====================

    public static GlobalBalance toDomain(GlobalBalanceEntity entity) {
        return new GlobalBalance(
                entity.getPortfolioId(),
                entity.getAvailableBalance(),
                entity.getReservedBalance(),
                entity.getRealizedBalance(),
                entity.getInitialCapital(),
                entity.getBaseCurrency(),
                entity.getTotalFeesPaid(),
                entity.getLastExecutionTime(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }
}
