package com.marmitt.application.spring.config.core;

import com.marmitt.core.application.usecase.reconciliation.ReconcileTradesUseCase;
import com.marmitt.core.ports.inbound.reconciliation.ReconcileTradesPort;
import com.marmitt.core.ports.outbound.exchange.TradeHistoryQueryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnExpression("!'${binance.api-key:}'.isBlank() && !'${binance.api-secret:}'.isBlank()")
public class ReconciliationConfig {

    @Bean
    public ReconcileTradesPort reconcileTradesUseCase(
            TradeHistoryQueryPort tradeHistoryQueryPort,
            StrategyRunnerRepositoryPort strategyRunnerRepository) {
        return new ReconcileTradesUseCase(tradeHistoryQueryPort, strategyRunnerRepository);
    }
}
