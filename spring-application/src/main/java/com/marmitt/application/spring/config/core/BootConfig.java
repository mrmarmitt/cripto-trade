package com.marmitt.application.spring.config.core;

import com.marmitt.core.application.usecase.boot.RunBootSequenceUseCase;
import com.marmitt.core.application.usecase.portfolio.PortfolioBootSanityUseCase;
import com.marmitt.core.application.usecase.portfolio.PortfolioReservationTtlUseCase;
import com.marmitt.core.application.usecase.portfolio.PortfolioZombieDetectionUseCase;
import com.marmitt.core.application.usecase.runner.RunnerBootRecoveryUseCase;
import com.marmitt.core.ports.outbound.repository.DeadLetterEntryRepositoryPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BootConfig {

    @Bean
    public RunBootSequenceUseCase runBootSequenceUseCase(
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
