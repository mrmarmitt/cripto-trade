package com.marmitt.application.spring.repository;

import com.marmitt.core.ports.outbound.repository.StrategyRepositoryPort;
import com.marmitt.core.ports.outbound.strategy.TradingStrategy;
import com.marmitt.strategy.impl.scenario.FilledBuyRestingSellCancelStrategy;
import com.marmitt.strategy.impl.scenario.ImmediateRoundTripStrategy;
import com.marmitt.strategy.impl.scenario.OverAllocationRejectStrategy;
import com.marmitt.strategy.impl.scenario.RestingBuyCancelStrategy;
import com.marmitt.strategy.impl.simple_moving_avager.SimpleMovingAverageStrategy;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Repository
public class InMemoryStrategyRepository implements StrategyRepositoryPort {

    private final Map<UUID, TradingStrategy> strategiesById = new ConcurrentHashMap<>();
    private final Map<String, TradingStrategy> strategiesByName = new ConcurrentHashMap<>();

    /**
     * Habilita o registro das estratégias de cenário (T35). Default {@code false} — elas nunca
     * existem no repositório em produção; ligar apenas em testnet/dev.
     */
    @Value("${ctrade.scenario-strategies.enabled:false}")
    private boolean scenarioStrategiesEnabled;

    @PostConstruct
    public void init() {
        log.info("Initializing InMemoryStrategyRepository...");

        // Registrar estratégias disponíveis
        registerStrategy(new SimpleMovingAverageStrategy());

        if (scenarioStrategiesEnabled) {
            log.warn("Scenario strategies ENABLED (ctrade.scenario-strategies.enabled=true) - "
                    + "apenas para testnet/dev, nao usar em producao");
            registerStrategy(new RestingBuyCancelStrategy());
            registerStrategy(new FilledBuyRestingSellCancelStrategy());
            registerStrategy(new ImmediateRoundTripStrategy());
            registerStrategy(new OverAllocationRejectStrategy());
        }

        log.info("InMemoryStrategyRepository initialized with {} strategy(ies): {}",
                strategiesById.size(),
                strategiesByName.keySet());
    }

    @Override
    public Optional<TradingStrategy> findById(UUID strategyId) {
        TradingStrategy strategy = strategiesById.get(strategyId);
        if (strategy != null) {
            log.debug("Strategy found by ID: {} -> {}", strategyId, strategy.getStrategyName());
        } else {
            log.debug("Strategy not found by ID: {}", strategyId);
        }
        return Optional.ofNullable(strategy);
    }

    @Override
    public Optional<TradingStrategy> findByName(String strategyName) {
        TradingStrategy strategy = strategiesByName.get(strategyName);
        if (strategy != null) {
            log.debug("Strategy found by name: {} -> {}", strategyName, strategy.getStrategyId());
        } else {
            log.debug("Strategy not found by name: {}", strategyName);
        }
        return Optional.ofNullable(strategy);
    }

    @Override
    public void registerStrategy(TradingStrategy strategy) {
        if (strategy == null) {
            log.warn("Attempted to register null strategy");
            return;
        }

        UUID strategyId = strategy.getStrategyId();
        String strategyName = strategy.getStrategyName();

        if (strategiesById.containsKey(strategyId)) {
            log.warn("Strategy with ID {} already registered, skipping: {}", strategyId, strategyName);
            return;
        }

        if (strategiesByName.containsKey(strategyName)) {
            log.warn("Strategy with name {} already registered, skipping", strategyName);
            return;
        }

        strategiesById.put(strategyId, strategy);
        strategiesByName.put(strategyName, strategy);

        log.info("Strategy registered - ID: {}, Name: {}, Version: {}, Enabled: {}",
                strategyId, strategyName, strategy.getStrategyVersion(), strategy.isEnabled());
    }
}
