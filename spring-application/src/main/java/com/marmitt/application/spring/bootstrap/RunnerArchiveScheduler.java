package com.marmitt.application.spring.bootstrap;

import com.marmitt.core.ports.inbound.runner.ManageRunnerLifecyclePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Arquiva periodicamente os runners em TERMINATING que já zeraram posição e ordens (T49, IG §13.4).
 * Só agenda; a regra fica no core.
 */
@Slf4j
@Component
public class RunnerArchiveScheduler {

    private final ManageRunnerLifecyclePort runnerLifecycle;

    public RunnerArchiveScheduler(ManageRunnerLifecyclePort runnerLifecycle) {
        this.runnerLifecycle = runnerLifecycle;
    }

    @Scheduled(
            fixedDelayString = "${runner.lifecycle.termination-check-interval-ms:10000}",
            initialDelayString = "${runner.lifecycle.termination-check-interval-ms:10000}"
    )
    public void scheduledTick() {
        try {
            runnerLifecycle.archiveCompleted();
        } catch (RuntimeException e) {
            log.error("runnerLifecycle: archive cycle failed - {}", e.getMessage(), e);
        }
    }
}
