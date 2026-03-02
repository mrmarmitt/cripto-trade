package com.marmitt.application.spring.bootstrap;

import com.marmitt.core.application.usecase.runner.RunnerBootRecoveryUseCase;
import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
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
    private final RunnerBootRecoveryUseCase runnerBootRecoveryUseCase;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        // Phase 1 (Infra) e Phase 2 (Portfolio) ainda serao implementadas.
        // Nesta versao focamos na execucao da Phase 3 (Runner recovery).
        log.info("bootOrchestrator: start");

        List<Portfolio> portfolios = portfolioRepository.findAll();
        List<RunnerBootRecoveryUseCase.RecoverySummary> summaries = portfolios.stream()
                .flatMap(this::loadRunnersByPortfolio)
                .filter(this::isEligibleForRecovery)
                .map(this::recoverRunner)
                .toList();

        log.info("bootOrchestrator: completed portfolios={} runners={}",
                portfolios.size(), summaries.size());
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
