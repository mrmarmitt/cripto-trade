package com.marmitt.core.application.usecase.runner;

import com.marmitt.core.dto.runner.response.RunnerHaltResult;
import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.exceptions.RunnerNotFoundException;
import com.marmitt.core.ports.inbound.runner.HaltRunnerPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;

@Slf4j
public class HaltRunnerUseCase implements HaltRunnerPort {

    private final StrategyRunnerRepositoryPort runnerRepository;

    public HaltRunnerUseCase(StrategyRunnerRepositoryPort runnerRepository) {
        this.runnerRepository = runnerRepository;
    }

    @Override
    public List<RunnerHaltResult> haltAll() {
        List<RunnerHaltResult> affected = runnerRepository.findAllByStatus(RunnerStatus.ACTIVE).stream()
                .map(runner -> {
                    runner.halt();
                    runnerRepository.save(runner);
                    log.info("kill-switch: runner halted - id={} strategy={} symbol={}",
                            runner.getId(), runner.getStrategyName(), runner.getSymbol());
                    return new RunnerHaltResult(
                            runner.getId(),
                            runner.getStrategyName(),
                            runner.getSymbol(),
                            RunnerStatus.ACTIVE,
                            RunnerStatus.HALTED);
                })
                .toList();
        log.info("kill-switch: halt-all complete - {} runner(s) halted", affected.size());
        return affected;
    }

    @Override
    public RunnerHaltResult haltById(UUID id) {
        var runner = runnerRepository.findById(id)
                .orElseThrow(() -> new RunnerNotFoundException(id));

        RunnerStatus previous = runner.getStatus();
        if (previous == RunnerStatus.ACTIVE) {
            runner.halt();
            runnerRepository.save(runner);
            log.info("kill-switch: runner halted - id={} strategy={} symbol={}",
                    runner.getId(), runner.getStrategyName(), runner.getSymbol());
        }

        return new RunnerHaltResult(runner.getId(), runner.getStrategyName(),
                runner.getSymbol(), previous, runner.getStatus());
    }
}
