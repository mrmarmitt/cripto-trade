package com.marmitt.core.dto.runner.response;

import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.enums.RunnerStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Builder
public record RunnerDto(
        UUID id,
        UUID portfolioId,
        UUID strategyId,
        String strategyName,
        String symbol,
        String exchangeId,
        Set<String> allowedMarketDataSources,
        RunnerStatus status,
        Instant createdAt
) {
    public static RunnerDto fromDomain(StrategyRunner runner) {
        return RunnerDto.builder()
                .id(runner.getId())
                .portfolioId(runner.getPortfolioId())
                .strategyId(runner.getStrategyId())
                .strategyName(runner.getStrategyName())
                .symbol(runner.getSymbol())
                .exchangeId(runner.getExchangeId())
                .allowedMarketDataSources(runner.getAllowedMarketDataSources())
                .status(runner.getStatus())
                .createdAt(runner.getCreatedAt())
                .build();
    }
}

