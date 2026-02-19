package com.marmitt.application.spring.config.core;

import com.marmitt.core.application.usecase.portfolio.ConfirmExecutionService;
import com.marmitt.core.application.usecase.portfolio.HandleExecutionConfirmedService;
import com.marmitt.core.application.usecase.portfolio.HandleMarginReleaseService;
import com.marmitt.core.application.usecase.portfolio.ReserveCapitalService;
import com.marmitt.core.application.usecase.portfolio.ReleaseMarginService;
import com.marmitt.core.application.usecase.runner.HandleOrderTerminationUseCase;
import com.marmitt.core.application.usecase.runner.PortfolioContextBuilder;
import com.marmitt.core.application.usecase.runner.ProcessTradeSignalService;
import com.marmitt.core.application.usecase.runner.ProcessTradeSignalUseCase;
import com.marmitt.core.application.usecase.runner.RunnerLifecycleUseCase;
import com.marmitt.core.ports.inbound.runner.HandleOrderTerminationPort;
import com.marmitt.core.ports.inbound.runner.ProcessTradeSignalPort;
import com.marmitt.core.ports.inbound.runner.ProcessTradeSignalTransactionPort;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.exchange.OrderDispatchPort;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.ListenerRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração Spring dos serviços do fluxo Runner (F2-01 e subsequentes).
 * <p>
 * Registra os serviços provisórios de capital e o {@code ProcessTradeSignalService}.
 * Os serviços marcados com {@code @implNote} serão absorvidos pelos fluxos corretos
 * durante refatoração futura (F2-xx).
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 6.2.1</a>
 */
@Configuration
public class RunnerConfig {

    // ── Serviços de capital provisórios (F1-11, absorção futura em F2-xx) ────

    @Bean
    public ReserveCapitalService reserveCapital(
            StrategyRunnerRepositoryPort runnerRepository,
            PortfolioRepositoryPort portfolioRepository,
            GlobalBalanceRepositoryPort globalBalanceRepository
    ) {
        return new ReserveCapitalService(runnerRepository, portfolioRepository, globalBalanceRepository);
    }

    @Bean
    public ConfirmExecutionService confirmExecution(EventPublisherPort eventPublisher) {
        return new ConfirmExecutionService(eventPublisher);
    }

    @Bean
    public ReleaseMarginService releaseMargin(EventPublisherPort eventPublisher) {
        return new ReleaseMarginService(eventPublisher);
    }

    @Bean
    public HandleExecutionConfirmedService handleExecutionConfirmed(
            StrategyRunnerRepositoryPort runnerRepository,
            GlobalBalanceRepositoryPort globalBalanceRepository
    ) {
        return new HandleExecutionConfirmedService(runnerRepository, globalBalanceRepository);
    }

    @Bean
    public HandleMarginReleaseService handleMarginRelease(
            StrategyRunnerRepositoryPort runnerRepository,
            GlobalBalanceRepositoryPort globalBalanceRepository
    ) {
        return new HandleMarginReleaseService(runnerRepository, globalBalanceRepository);
    }

    // ── UseCase do fluxo HandleOrderTermination (F2-03) ──────────────────────

    @Bean
    public HandleOrderTerminationUseCase handleOrderTermination(
            StrategyRunnerRepositoryPort runnerRepository,
            ReleaseMarginService releaseMarginService
    ) {
        return new HandleOrderTerminationUseCase(runnerRepository, releaseMarginService);
    }

    // ── Serviços do fluxo ProcessTradeSignal (F2-01) ─────────────────────────

    @Bean
    public ProcessTradeSignalService processTradeSignal(
            StrategyRunnerRepositoryPort runnerRepository,
            ReserveCapitalService reserveCapitalService,
            OrderDispatchPort orderDispatchPort
    ) {
        return new ProcessTradeSignalService(runnerRepository, reserveCapitalService, orderDispatchPort);
    }

    // ── UseCase do fluxo ProcessTradeSignal (F2-01) ──────────────────────────

    @Bean
    public ProcessTradeSignalUseCase processTradeSignalUseCase(
            ProcessTradeSignalService processTradeSignalService,
            ProcessTradeSignalTransactionPort transactionPort,
            HandleOrderTerminationPort terminationPort
    ) {
        return new ProcessTradeSignalUseCase(processTradeSignalService, transactionPort, terminationPort);
    }

    // ── Serviços do fluxo Runner Lifecycle (F2-04) ────────────────────────────

    @Bean
    public PortfolioContextBuilder portfolioContextBuilder(
            StrategyRunnerRepositoryPort runnerRepository,
            GlobalBalanceRepositoryPort globalBalanceRepository
    ) {
        return new PortfolioContextBuilder(runnerRepository, globalBalanceRepository);
    }

    @Bean
    public RunnerLifecycleUseCase runnerLifecycle(
            StrategyRunnerRepositoryPort runnerRepository,
            StrategyRepositoryPort strategyRepository,
            ListenerRepositoryPort listenerRepository,
            PortfolioContextBuilder portfolioContextBuilder,
            ProcessTradeSignalPort processTradeSignalPort,
            HandleOrderTerminationPort terminationPort
    ) {
        return new RunnerLifecycleUseCase(
                runnerRepository, strategyRepository, listenerRepository,
                portfolioContextBuilder, processTradeSignalPort, terminationPort);
    }
}
