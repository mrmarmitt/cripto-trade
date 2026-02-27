package com.marmitt.core.dto.portfolio;

import com.marmitt.core.domain.portfolio.GlobalBalance;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record GlobalBalanceDto(
        UUID portfolioId,
        BigDecimal availableBalance,
        BigDecimal reservedBalance,
        BigDecimal realizedBalance,
        BigDecimal initialCapital,
        String baseCurrency,
        BigDecimal totalFeesPaid,
        Instant lastExecutionTime,
        Instant updatedAt
) {

    public static GlobalBalanceDto fromDomain(GlobalBalance balance) {
        return GlobalBalanceDto.builder()
                .portfolioId(balance.getPortfolioId())
                .availableBalance(balance.getAvailableBalance())
                .reservedBalance(balance.getReservedBalance())
                .realizedBalance(balance.getRealizedBalance())
                .initialCapital(balance.getInitialCapital())
                .baseCurrency(balance.getBaseCurrency())
                .totalFeesPaid(balance.getTotalFeesPaid())
                .lastExecutionTime(balance.getLastExecutionTime())
                .updatedAt(balance.getUpdatedAt())
                .build();
    }
}
