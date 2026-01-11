package com.marmitt.application.spring.repository;

import com.marmitt.core.ports.outbound.repository.StrategyRepositoryPort;
import com.marmitt.core.ports.outbound.strategy.TradingStrategy;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class InMemoryStrategyRepository implements StrategyRepositoryPort {

    @Override
    public Optional<TradingStrategy> findById(UUID strategyId) {
        return Optional.empty();
    }

    @Override
    public Optional<TradingStrategy> findByName(String strategyName) {
        return Optional.empty();
    }

    @Override
    public void registerStrategy(TradingStrategy strategy) {

    }
}
