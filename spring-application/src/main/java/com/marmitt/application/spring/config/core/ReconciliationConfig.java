package com.marmitt.application.spring.config.core;

import com.marmitt.core.application.usecase.reconciliation.ReconcileTradesUseCase;
import com.marmitt.core.ports.inbound.reconciliation.ReconcileTradesPort;
import com.marmitt.core.ports.outbound.exchange.TradeHistoryQueryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReconciliationConfig {

    @Bean
    @ConditionalOnBean(TradeHistoryQueryPort.class)
    public ReconcileTradesPort reconcileTradesUseCase(
            TradeHistoryQueryPort tradeHistoryQueryPort,
            StrategyRunnerRepositoryPort strategyRunnerRepository) {
        return new ReconcileTradesUseCase(tradeHistoryQueryPort, strategyRunnerRepository);
    }
}
