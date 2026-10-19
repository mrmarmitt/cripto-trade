package com.marmitt.core.ports.outbound.repository;

import com.marmitt.core.ports.outbound.strategy.TradingStrategy;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface StrategyRepositoryPort {

    Optional<TradingStrategy> findById(UUID strategyId);

    void registerStrategy(TradingStrategy strategy);
}