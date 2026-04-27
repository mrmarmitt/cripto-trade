package com.marmitt.application.spring.config.core;

import com.marmitt.core.application.usecase.boot.RunBootSequenceUseCase;
import com.marmitt.core.application.usecase.runner.RunnerBootRecoveryUseCase;
import com.marmitt.core.application.usecase.runner.orderconciliation.ConciliationOrderUpdateExecutor;
import com.marmitt.core.ports.inbound.boot.RunBootSequencePort;
import com.marmitt.core.ports.outbound.repository.DeadLetterEntryRepositoryPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BootConfig {

    @Bean
    public RunBootSequencePort runBootSequenceUseCase(
            PortfolioRepositoryPort portfolioRepository,
            StrategyRunnerRepositoryPort strategyRunnerRepository,
            ExchangeAdapterRepositoryPort exchangeAdapterRepository,
            GlobalBalanceRepositoryPort globalBalanceRepository,
            DeadLetterEntryRepositoryPort deadLetterEntryRepository,
            ConciliationOrderUpdateExecutor conciliationOrderUpdate,
            RunnerBootRecoveryUseCase runnerBootRecoveryUseCase
    ) {
        return new RunBootSequenceUseCase(
                portfolioRepository,
                strategyRunnerRepository,
                exchangeAdapterRepository,
                globalBalanceRepository,
                deadLetterEntryRepository,
                conciliationOrderUpdate,
                runnerBootRecoveryUseCase
        );
    }
}
