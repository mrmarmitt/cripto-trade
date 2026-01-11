package com.marmitt.application.spring.config.core;

import com.marmitt.core.application.usecase.portfolio.CreatePortfolioUseCase;
import com.marmitt.core.ports.inbound.portfolio.CreatePortfolioPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PortfolioConfig {

    @Bean
    public CreatePortfolioPort createPortfolio(
            PortfolioRepositoryPort portfolioRepository,
            StrategyRepositoryPort strategyRepository,
            ExchangeAdapterRepositoryPort exchangeAdapterRepository
    ) {
        return new CreatePortfolioUseCase(
                portfolioRepository,
                strategyRepository,
                exchangeAdapterRepository
        );
    }
}
