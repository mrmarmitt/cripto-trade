package com.marmitt.core.application.usecase.runner;

import com.marmitt.core.domain.runner.RunnerStatusCause;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.runner.response.RunnerTransitionResult;
import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.exceptions.RunnerNotFoundException;
import com.marmitt.core.ports.inbound.runner.HaltRunnerPort;
import com.marmitt.core.ports.outbound.metrics.OperationalMetricsPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
public class HaltRunnerUseCase implements HaltRunnerPort {

    private final StrategyRunnerRepositoryPort runnerRepository;
    private final OperationalMetricsPort metrics;

    public HaltRunnerUseCase(StrategyRunnerRepositoryPort runnerRepository, OperationalMetricsPort metrics) {
        this.runnerRepository = runnerRepository;
        this.metrics = metrics;
    }

    @Override
    public List<RunnerTransitionResult> haltAll(RunnerStatusCause cause) {
        // Use canAcceptSignals() as eligibility: ACTIVE or TERMINATING outside reconciliation, so that
        // boot recovery in progress is not interrupted by a concurrent halt-all (T49: TERMINATING too).
        List<RunnerTransitionResult> affected = Stream.of(RunnerStatus.ACTIVE, RunnerStatus.TERMINATING)
                .flatMap(status -> runnerRepository.findAllByStatus(status).stream())
                .filter(StrategyRunner::canAcceptSignals)
                .map(runner -> {
                    RunnerStatus previous = runner.getStatus();
                    runner.halt(cause);
                    runnerRepository.save(runner);
                    metrics.recordTransition(previous, RunnerStatus.HALTED);
                    log.info("kill-switch: runner halted - id={} strategy={} symbol={} previous={}",
                            runner.getId(), runner.getStrategyName(), runner.getSymbol(), previous);
                    return RunnerTransitionResult.of(runner, previous);
                })
                .toList();
        log.info("kill-switch: halt-all complete - {} runner(s) halted", affected.size());
        return affected;
    }

    @Override
    public RunnerTransitionResult haltById(UUID id, RunnerStatusCause cause) {
        var runner = runnerRepository.findById(id)
                .orElseThrow(() -> new RunnerNotFoundException(id));

        RunnerStatus previous = runner.getStatus();
        // Mesma elegibilidade do halt-all: ACTIVE ou TERMINATING fora de reconciliacao (T49).
        if (runner.canAcceptSignals()) {
            runner.halt(cause);
            runnerRepository.save(runner);
            metrics.recordTransition(previous, RunnerStatus.HALTED);
            log.info("kill-switch: runner halted - id={} strategy={} symbol={}",
                    runner.getId(), runner.getStrategyName(), runner.getSymbol());
        }

        return RunnerTransitionResult.of(runner, previous);
    }
}
