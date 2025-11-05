package com.marmitt.core.ports.outbound.repository;

import com.marmitt.core.ports.outbound.strategy.TradingStrategy;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface StrategyRepositoryPort {

    Optional<TradingStrategy> findById(UUID strategyId);

    Optional<TradingStrategy> findByName(String strategyName);

    void save(TradingStrategy strategy);

    Collection<TradingStrategy> findAll();

    Collection<TradingStrategy> findAllEnabled();

    boolean existsById(UUID strategyId);

    void deleteById(UUID strategyId);
}