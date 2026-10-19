package com.marmitt.core.application.usecase.strategy;

import com.marmitt.core.dto.strategy.StrategyContextDto;
import com.marmitt.core.dto.strategy.StrategyInputDto;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.ports.outbound.strategy.StrategyEvaluationPort;
import com.marmitt.core.ports.outbound.strategy.TradingStrategy;

import java.util.Objects;
import java.util.UUID;

/**
 * Estratégia que roda fora do processo (T41). É só mais uma {@link TradingStrategy}: o pipeline de
 * sinal não sabe que a decisão veio da rede. Identidade (id, nome, versão) vem do catálogo,
 * conferida contra o próprio serviço antes do registro.
 */
class RemoteTradingStrategy implements TradingStrategy {

    private final UUID strategyRef;
    private final String name;
    private final String version;
    private final StrategyEvaluationPort evaluation;
    private volatile boolean enabled;

    RemoteTradingStrategy(UUID strategyRef, String name, String version, StrategyEvaluationPort evaluation) {
        this.strategyRef = Objects.requireNonNull(strategyRef, "strategyRef cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.version = Objects.requireNonNull(version, "version cannot be null");
        this.evaluation = Objects.requireNonNull(evaluation, "evaluation cannot be null");
        this.enabled = true;
    }

    @Override
    public UUID getStrategyId() {
        return strategyRef;
    }

    @Override
    public StrategyOutputDto executeStrategy(StrategyInputDto inputData, StrategyContextDto strategyContext) {
        return evaluation.evaluate(strategyRef, inputData, strategyContext);
    }

    @Override
    public String getStrategyName() {
        return name;
    }

    @Override
    public String getStrategyVersion() {
        return version;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
