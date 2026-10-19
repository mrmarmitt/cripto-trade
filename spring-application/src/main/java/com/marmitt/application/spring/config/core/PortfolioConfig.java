package com.marmitt.application.spring.config.core;

import com.marmitt.application.spring.bootstrap.CircuitBreakerProperties;
import com.marmitt.application.spring.service.TransactionalManageDeadLetterPort;
import com.marmitt.core.application.usecase.portfolio.EvaluateDrawdownUseCase;
import com.marmitt.core.application.usecase.portfolio.ReportOperationalStateUseCase;
import com.marmitt.application.spring.bootstrap.OperationalStateProperties;
import com.marmitt.core.ports.inbound.portfolio.ReportOperationalStatePort;
import com.marmitt.core.ports.outbound.metrics.OperationalMetricsPort;
import com.marmitt.core.ports.inbound.portfolio.EvaluateDrawdownPort;
import com.marmitt.core.ports.outbound.marketdata.LatestPricePort;
import com.marmitt.core.ports.outbound.metrics.CircuitBreakerMetricsPort;
import com.marmitt.core.ports.outbound.notification.ErrorNotificationPort;
import com.marmitt.core.ports.outbound.repository.EquitySnapshotRepositoryPort;
import com.marmitt.core.ports.outbound.repository.DeadLetterReprocessingPort;
import com.marmitt.core.application.usecase.portfolio.CreatePortfolioUseCase;
import com.marmitt.core.application.usecase.portfolio.ManageDeadLetterUseCase;
import com.marmitt.core.application.usecase.portfolio.ManageSafeModeUseCase;
import com.marmitt.core.application.usecase.portfolio.QueryPortfolioUseCase;
import com.marmitt.core.ports.inbound.portfolio.CreatePortfolioPort;
import com.marmitt.core.ports.inbound.portfolio.ManageDeadLetterPort;
import com.marmitt.core.ports.inbound.portfolio.ManageSafeModePort;
import com.marmitt.core.ports.inbound.portfolio.QueryPortfolioPort;
import com.marmitt.core.ports.outbound.repository.DeadLetterEntryRepositoryPort;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

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
            GlobalBalanceRepositoryPort globalBalanceRepository,
            LatestPricePort latestPrices
    ) {
        return new QueryPortfolioUseCase(
                portfolioRepository,
                strategyRunnerRepository,
                globalBalanceRepository,
                latestPrices
        );
    }

    @Bean
    public ManageSafeModePort manageSafeMode(
            PortfolioRepositoryPort portfolioRepository,
            StrategyRunnerRepositoryPort strategyRunnerRepository
    ) {
        return new ManageSafeModeUseCase(portfolioRepository, strategyRunnerRepository);
    }

    @Bean
    public EvaluateDrawdownPort evaluateDrawdown(
            PortfolioRepositoryPort portfolioRepository,
            GlobalBalanceRepositoryPort globalBalanceRepository,
            StrategyRunnerRepositoryPort strategyRunnerRepository,
            LatestPricePort latestPrices,
            EquitySnapshotRepositoryPort equitySnapshots,
            ManageSafeModePort manageSafeMode,
            ErrorNotificationPort errorNotification,
            CircuitBreakerMetricsPort circuitBreakerMetrics,
            CircuitBreakerProperties circuitBreakerProperties
    ) {
        return new EvaluateDrawdownUseCase(portfolioRepository, globalBalanceRepository, strategyRunnerRepository,
                latestPrices, equitySnapshots, manageSafeMode, errorNotification, circuitBreakerMetrics,
                circuitBreakerProperties.getMaxDrawdownPercent(), Clock.systemUTC());
    }

    @Bean
    public ReportOperationalStatePort reportOperationalState(
            PortfolioRepositoryPort portfolioRepository,
            GlobalBalanceRepositoryPort globalBalanceRepository,
            StrategyRunnerRepositoryPort strategyRunnerRepository,
            OperationalMetricsPort operationalMetrics,
            OperationalStateProperties operationalStateProperties
    ) {
        return new ReportOperationalStateUseCase(portfolioRepository, globalBalanceRepository,
                strategyRunnerRepository, operationalMetrics,
                Duration.ofMillis(operationalStateProperties.getTerminationTimeoutMs()), Instant::now);
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
