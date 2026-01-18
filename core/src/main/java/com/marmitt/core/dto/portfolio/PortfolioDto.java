package com.marmitt.core.dto.portfolio;

import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.domain.portfolio.Position;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Builder
public record PortfolioDto(
        UUID id,
        String name,
        UUID strategyId,
        String strategyName,
        String symbol,
        String orderExecutionExchange,
        Set<String> allowedMarketDataSources,
        BalanceDto balance,
        PositionDto position,
        int transactionCount,
        boolean isActive,
        Instant createdAt,
        Instant lastExecutionTime
) {

    @Builder
    public record BalanceDto(
            String totalCapital,
            String availableBalance,
            String allocatedBalance,
            String currency
    ) {}

    @Builder
    public record PositionDto(
            String symbol,
            String quantity,
            String quantityCurrency,
            String averagePrice,
            String currentPrice,
            String priceCurrency,
            String currentValue,
            String unrealizedPnL,
            BigDecimal unrealizedPnLPercentage
    ) {}

    public static PortfolioDto fromDomain(Portfolio portfolio) {
        BalanceDto balanceDto = BalanceDto.builder()
                .totalCapital(portfolio.getBalance().getTotal().amount().toPlainString())
                .availableBalance(portfolio.getBalance().getAvailable().amount().toPlainString())
                .allocatedBalance(portfolio.getBalance().getAllocated().amount().toPlainString())
                .currency(portfolio.getBalance().getTotal().currency())
                .build();

        PositionDto positionDto = null;
        if (portfolio.getPosition() != null) {
            Position position = portfolio.getPosition();
            positionDto = PositionDto.builder()
                    .symbol(position.getSymbol().value())
                    .quantity(position.getQuantity().amount().toPlainString())
                    .quantityCurrency(position.getQuantity().currency())
                    .averagePrice(position.getAveragePrice().amount().toPlainString())
                    .currentPrice(position.getCurrentPrice().amount().toPlainString())
                    .priceCurrency(position.getAveragePrice().currency())
                    .currentValue(position.getCurrentValue().amount().toPlainString())
                    .unrealizedPnL(position.getUnrealizedPnL().amount().toPlainString())
                    .unrealizedPnLPercentage(position.getUnrealizedPnLPercentage().amount())
                    .build();
        }

        return PortfolioDto.builder()
                .id(portfolio.getId())
                .name(portfolio.getName())
                .strategyId(portfolio.getStrategyId())
                .strategyName(portfolio.getStrategyName())
                .symbol(portfolio.getSymbol().value())
                .orderExecutionExchange(portfolio.getOrderExecutionExchange())
                .allowedMarketDataSources(portfolio.getAllowedMarketDataSources())
                .balance(balanceDto)
                .position(positionDto)
                .transactionCount(portfolio.getTransactions().size())
                .isActive(portfolio.isActive())
                .createdAt(portfolio.getCreatedAt())
                .lastExecutionTime(portfolio.getLastExecutionTime())
                .build();
    }
}
