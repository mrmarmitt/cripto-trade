package com.marmitt.application.spring.bootstrap;

import com.marmitt.core.application.usecase.runner.RunnerBootRecoveryUseCase;
import com.marmitt.core.application.usecase.portfolio.PortfolioBootSanityUseCase;
import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.exchange.boot.ExchangeBootReadiness;
import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Orquestrador de boot em nivel de aplicacao.
 *
 * <p>Responsabilidade nesta fase:
 * <ul>
 *   <li>Demonstrar a ordem de fases do boot recovery.</li>
 *   <li>Disparar o RunnerBootRecoveryUseCase para cada runner elegivel.</li>
 *   <li>Executar saneamento/reconciliacao por runner e registrar resumo em log.</li>
 * </ul>
 *
 * <p>Ativacao:
 * <ul>
 *   <li>{@code runner.boot.orchestrator-enabled=true}</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "runner.boot.orchestrator-enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class BootOrchestrator {

    private final PortfolioRepositoryPort portfolioRepository;
    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final ExchangeAdapterRepositoryPort exchangeAdapterRepository;
    private final PortfolioBootSanityUseCase portfolioBootSanityUseCase;
    private final RunnerBootPhase2Properties phase2Properties;
    private final RunnerBootRecoveryUseCase runnerBootRecoveryUseCase;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("bootOrchestrator: start");

        List<Portfolio> portfolios = portfolioRepository.findAll();
        List<StrategyRunner> eligibleRunners = portfolios.stream()
                .flatMap(this::loadRunnersByPortfolio)
                .filter(this::isEligibleForRecovery)
                .toList();

        runPhase1InfrastructureReadiness(eligibleRunners);
        runPhase2PortfolioSanity(portfolios, eligibleRunners);

        List<RunnerBootRecoveryUseCase.RecoverySummary> summaries = eligibleRunners.stream()
                .map(this::recoverRunner)
                .toList();

        log.info("bootOrchestrator: completed portfolios={} runners={}",
                portfolios.size(), summaries.size());
    }

    private void runPhase1InfrastructureReadiness(List<StrategyRunner> runners) {
        Set<String> exchanges = runners.stream()
                .map(StrategyRunner::getExchangeId)
                .filter(exchange -> exchange != null && !exchange.isBlank())
                .map(String::toUpperCase)
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));

        if (exchanges.isEmpty()) {
            log.info("bootOrchestrator.phase1: no exchange to validate");
            return;
        }

        log.info("bootOrchestrator.phase1: start exchanges={}", exchanges.size());

        for (String exchange : exchanges) {
            ExchangeBootReadiness readiness = exchangeAdapterRepository.findBootReadinessByName(exchange)
                    .orElseThrow(() -> new IllegalStateException(
                            "Boot readiness capability is not registered for exchange=" + exchange))
                    .checkBootReadiness();

            if (!readiness.ready()) {
                throw new IllegalStateException("Phase1 readiness failed exchange=" + exchange
                        + " code=" + readiness.code()
                        + " message=" + readiness.message());
            }

            log.info("bootOrchestrator.phase1: exchange={} ready code={} message={}",
                    exchange, readiness.code(), readiness.message());
        }

        log.info("bootOrchestrator.phase1: completed exchanges={}", exchanges.size());
    }

    private void runPhase2PortfolioSanity(List<Portfolio> portfolios, List<StrategyRunner> eligibleRunners) {
        Phase2Mode mode = phase2Properties.getMode();
        log.info("bootOrchestrator.phase2: start portfolios={} mode={} absoluteTolerance={} percentTolerance={}",
                portfolios.size(), mode, phase2Properties.getAbsoluteTolerance(), phase2Properties.getPercentTolerance());

        for (Portfolio portfolio : portfolios) {
            Set<String> exchanges = eligibleRunners.stream()
                    .filter(runner -> runner.getPortfolioId().equals(portfolio.getId()))
                    .map(StrategyRunner::getExchangeId)
                    .filter(exchange -> exchange != null && !exchange.isBlank())
                    .map(String::toUpperCase)
                    .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));

            if (exchanges.isEmpty()) {
                log.debug("bootOrchestrator.phase2: portfolio={} skipped - no eligible runner exchange",
                        portfolio.getId());
                continue;
            }

            for (String exchange : exchanges) {
                PortfolioBootSanityUseCase.PortfolioBootSanityResult result =
                        portfolioBootSanityUseCase.execute(
                                portfolio.getId(),
                                exchange,
                                phase2Properties.getAbsoluteTolerance(),
                                phase2Properties.getPercentTolerance()
                        );

                switch (result.status()) {
                    case OK -> log.info(
                            "bootOrchestrator.phase2: portfolio={} exchange={} status={} localTotal={} exchangeTotal={} drift={} driftPercent={}",
                            result.portfolioId(),
                            result.exchangeId(),
                            result.status(),
                            result.localTotal(),
                            result.exchangeTotal(),
                            result.absoluteDrift(),
                            result.driftPercent()
                    );
                    case SKIPPED -> log.warn(
                            "bootOrchestrator.phase2: portfolio={} exchange={} status={} code={} message={}",
                            result.portfolioId(),
                            result.exchangeId(),
                            result.status(),
                            result.code(),
                            result.message()
                    );
                    case FAILED -> log.warn(
                            "bootOrchestrator.phase2: portfolio={} exchange={} status={} code={} message={} localTotal={} exchangeTotal={} drift={} driftPercent={}",
                            result.portfolioId(),
                            result.exchangeId(),
                            result.status(),
                            result.code(),
                            result.message(),
                            result.localTotal(),
                            result.exchangeTotal(),
                            result.absoluteDrift(),
                            result.driftPercent()
                    );
                }

                if (result.status() == PortfolioBootSanityUseCase.SanityStatus.FAILED
                        && mode == Phase2Mode.FAIL_FAST) {
                    throw new IllegalStateException("Phase2 sanity failed portfolio=" + result.portfolioId()
                            + " exchange=" + result.exchangeId()
                            + " code=" + result.code()
                            + " message=" + result.message());
                }
            }
        }

        log.info("bootOrchestrator.phase2: completed");
    }

    private Stream<StrategyRunner> loadRunnersByPortfolio(Portfolio portfolio) {
        return strategyRunnerRepository.findByPortfolioId(portfolio.getId()).stream();
    }

    private boolean isEligibleForRecovery(StrategyRunner runner) {
        return runner.getStatus() != RunnerStatus.ARCHIVED;
    }

    private RunnerBootRecoveryUseCase.RecoverySummary recoverRunner(StrategyRunner runner) {
        RunnerBootRecoveryUseCase.RecoverySummary summary = runnerBootRecoveryUseCase.recoverRunner(runner);

        log.info("bootOrchestrator: runner={} inFlight={} zombies={} limbo={}",
                summary.runnerId(), summary.inFlightCount(), summary.zombiesCount(), summary.limboCount());

        // Verbose somente em debug para nao poluir log de producao.
        if (log.isDebugEnabled()) {
            summary.notes().forEach(note ->
                    log.debug("bootOrchestrator: runner={} plan={}", summary.runnerId(), note));
        }

        return summary;
    }
}
