package com.marmitt.core.dto.runner.response;

import com.marmitt.core.enums.RunnerStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Builder
public record CreateRunnerResponse(
        UUID runnerId,
        UUID portfolioId,
        UUID strategyId,
        String strategyName,
        String symbol,
        String exchangeName,
        Set<String> allowedMarketDataSources,
        RunnerStatus status,
        Instant createdAt,
        String message
) {

    public static CreateRunnerResponse success(
            UUID runnerId,
            UUID portfolioId,
            UUID strategyId,
            String strategyName,
            String symbol,
            String exchangeName,
            Set<String> allowedMarketDataSources,
            RunnerStatus status,
            Instant createdAt
    ) {
        return CreateRunnerResponse.builder()
                .runnerId(runnerId)
                .portfolioId(portfolioId)
                .strategyId(strategyId)
                .strategyName(strategyName)
                .symbol(symbol)
                .exchangeName(exchangeName)
                .allowedMarketDataSources(allowedMarketDataSources)
                .status(status)
                .createdAt(createdAt)
                .message("Runner created successfully")
                .build();
    }

    public static CreateRunnerResponse failure(String message) {
        return CreateRunnerResponse.builder()
                .message(message)
                .build();
    }
}

