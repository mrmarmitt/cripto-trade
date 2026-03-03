package com.marmitt.application.spring.config.core;

import com.marmitt.core.application.usecase.portfolio.CreatePortfolioUseCase;
import com.marmitt.core.application.usecase.portfolio.PortfolioBootSanityUseCase;
import com.marmitt.core.application.usecase.portfolio.QueryPortfolioUseCase;
import com.marmitt.core.ports.inbound.portfolio.CreatePortfolioPort;
import com.marmitt.core.ports.inbound.portfolio.QueryPortfolioPort;
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
}
