package com.marmitt.core.dto.strategy;

import com.marmitt.core.domain.StrategyOutput;
import com.marmitt.core.domain.Symbol;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public record StrategyExecutionResult(
        String strategyName,
        Symbol symbol,
        boolean success,
        StrategyOutput output,
        String errorMessage,
        Duration executionTime,
        Instant timestamp,
        Map<String, Object> metrics
) {
    public static StrategyExecutionResult success(String strategyName, Symbol symbol, 
                                                StrategyOutput output, Duration executionTime) {
        return new StrategyExecutionResult(
                strategyName,
                symbol,
                true,
                output,
                null,
                executionTime,
                Instant.now(),
                Map.of()
        );
    }
    
    public static StrategyExecutionResult failure(String strategyName, Symbol symbol, 
                                                String errorMessage, Duration executionTime) {
        return new StrategyExecutionResult(
                strategyName,
                symbol,
                false,
                null,
                errorMessage,
                executionTime,
                Instant.now(),
                Map.of()
        );
    }
    
    public static StrategyExecutionResult withMetrics(String strategyName, Symbol symbol, 
                                                    StrategyOutput output, Duration executionTime, 
                                                    Map<String, Object> metrics) {
        return new StrategyExecutionResult(
                strategyName,
                symbol,
                true,
                output,
                null,
                executionTime,
                Instant.now(),
                metrics
        );
    }
    
    public boolean hasOutput() {
        return output != null;
    }
    
    public boolean hasError() {
        return errorMessage != null;
    }
    
    public boolean shouldTrade() {
        return success && hasOutput() && output.shouldTrade();
    }
}