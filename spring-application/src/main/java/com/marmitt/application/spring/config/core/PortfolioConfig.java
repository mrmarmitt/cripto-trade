package com.marmitt.application.spring.config.core;

import com.marmitt.application.spring.service.TransactionalManageDeadLetterPort;
import com.marmitt.core.ports.outbound.repository.DeadLetterReprocessingPort;
import com.marmitt.core.application.usecase.portfolio.CreatePortfolioUseCase;
import com.marmitt.core.application.usecase.portfolio.ManageDeadLetterUseCase;
import com.marmitt.core.application.usecase.portfolio.PortfolioBootSanityUseCase;
import com.marmitt.core.application.usecase.portfolio.PortfolioReservationTtlUseCase;
import com.marmitt.core.application.usecase.portfolio.PortfolioZombieDetectionUseCase;
import com.marmitt.core.application.usecase.portfolio.QueryPortfolioUseCase;
import com.marmitt.core.application.usecase.runner.orderconciliation.ConciliationOrderUpdateExecutor;
import com.marmitt.core.ports.inbound.portfolio.CreatePortfolioPort;
import com.marmitt.core.ports.inbound.portfolio.ManageDeadLetterPort;
import com.marmitt.core.ports.inbound.portfolio.QueryPortfolioPort;
import com.marmitt.core.ports.outbound.repository.DeadLetterEntryRepositoryPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PortfolioConfig {

    @Bean
    public CreatePortfolioPort createPortfolio(
            PortfolioRepositoryPort portfolioRepository,
            GlobalBalanceRepositoryPort globalBalanceRepository
    ) {
        return new CreatePortfolioUseCase(
                portfolioRepository,
                globalBalanceRepository
        );
    }

    @Bean
    public QueryPortfolioPort queryPortfolio(
            PortfolioRepositoryPort portfolioRepository,
            StrategyRunnerRepositoryPort strategyRunnerRepository,
            GlobalBalanceRepositoryPort globalBalanceRepository
    ) {
        return new QueryPortfolioUseCase(
                portfolioRepository,
                strategyRunnerRepository,
                globalBalanceRepository
        );
    }

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
    public ManageDeadLetterUseCase manageDeadLetterUseCase(
            DeadLetterEntryRepositoryPort deadLetterEntryRepository,
            DeadLetterReprocessingPort deadLetterReprocessingPort
    ) {
        return new ManageDeadLetterUseCase(deadLetterEntryRepository, deadLetterReprocessingPort);
    }

    @Bean
    public ManageDeadLetterPort manageDeadLetter(
            ManageDeadLetterUseCase manageDeadLetterUseCase
    ) {
        return new TransactionalManageDeadLetterPort(manageDeadLetterUseCase);
    }
}
