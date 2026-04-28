package com.marmitt.application.spring.config.core;

import com.marmitt.core.application.usecase.boot.RunBootSequenceUseCase;
import com.marmitt.core.application.usecase.boot.phase2.PortfolioBootSanityUseCase;
import com.marmitt.core.application.usecase.boot.phase2.PortfolioReservationTtlUseCase;
import com.marmitt.core.application.usecase.boot.phase2.PortfolioZombieDetectionUseCase;
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
    public PortfolioBootSanityUseCase portfolioBootSanityUseCase(
            GlobalBalanceRepositoryPort globalBalanceRepository,
            ExchangeAdapterRepositoryPort exchangeAdapterRepository
    ) {
        return new PortfolioBootSanityUseCase(globalBalanceRepository, exchangeAdapterRepository);
    }

    @Bean
    public PortfolioZombieDetectionUseCase portfolioZombieDetectionUseCase(
            StrategyRunnerRepositoryPort strategyRunnerRepository,
            ExchangeAdapterRepositoryPort exchangeAdapterRepository
    ) {
        return new PortfolioZombieDetectionUseCase(strategyRunnerRepository, exchangeAdapterRepository);
    }

    @Bean
    public PortfolioReservationTtlUseCase portfolioReservationTtlUseCase(
            StrategyRunnerRepositoryPort strategyRunnerRepository,
            ConciliationOrderUpdateExecutor conciliationOrderUpdate
    ) {
        return new PortfolioReservationTtlUseCase(strategyRunnerRepository, conciliationOrderUpdate);
    }

    @Bean
    public RunBootSequencePort runBootSequence(
            PortfolioRepositoryPort portfolioRepository,
            StrategyRunnerRepositoryPort strategyRunnerRepository,
            ExchangeAdapterRepositoryPort exchangeAdapterRepository,
            PortfolioBootSanityUseCase portfolioBootSanityUseCase,
            PortfolioReservationTtlUseCase portfolioReservationTtlUseCase,
            PortfolioZombieDetectionUseCase portfolioZombieDetectionUseCase,
            DeadLetterEntryRepositoryPort deadLetterEntryRepository,
            RunnerBootRecoveryUseCase runnerBootRecoveryUseCase
    ) {
        return new RunBootSequenceUseCase(
                portfolioRepository,
                strategyRunnerRepository,
                exchangeAdapterRepository,
                portfolioBootSanityUseCase,
                portfolioReservationTtlUseCase,
                portfolioZombieDetectionUseCase,
                deadLetterEntryRepository,
                runnerBootRecoveryUseCase
        );
    }
}
