package com.marmitt.application.spring.bootstrap;

import com.marmitt.core.dto.boot.BootExecutionCommand;
import com.marmitt.core.dto.boot.BootRunSnapshot;
import com.marmitt.core.enums.BootPhaseStatus;
import com.marmitt.core.enums.BootRunStatus;
import com.marmitt.core.ports.inbound.boot.RunBootSequencePort;
import com.marmitt.core.ports.outbound.boot.BootExecutionObserverPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "runner.boot.orchestrator-enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class BootOrchestrator {

    private final RunnerBootPhase1Properties phase1Properties;
    private final RunnerBootPhase2Properties phase2Properties;
    private final RunnerBootPhase3Properties phase3Properties;
    private final PortfolioSanityCheckProperties portfolioSanityCheckProperties;
    private final PortfolioReservationTtlProperties portfolioReservationTtlProperties;
    private final PortfolioZombieDetectionProperties portfolioZombieDetectionProperties;
    private final PortfolioCutoffProperties portfolioCutoffProperties;
    private final RunBootSequencePort runBootSequence;
    private final BootStatusTracker bootStatusTracker;
    private final BootMetricsRecorder bootMetricsRecorder;
    private final ApplicationEventPublisher applicationEventPublisher;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        BootExecutionCommand command = new BootExecutionCommand(
                phase1Properties.isEnabled(),
                phase2Properties.isEnabled(),
                phase3Properties.isEnabled(),
                portfolioSanityCheckProperties.isEnabled(),
                portfolioSanityCheckProperties.getThreshold(),
                portfolioZombieDetectionProperties.isEnabled(),
                portfolioCutoffProperties.isEnabled(),
                portfolioReservationTtlProperties.isEnabled(),
                portfolioReservationTtlProperties.getTtlMs(),
                phase2Properties.getMode(),
                phase2Properties.getAccountQueryPolicy()
        );

        BootExecutionObserverPort observer = buildObserver();
        try {
            runBootSequence.execute(command, observer);
        } catch (RuntimeException e) {
            log.error("bootOrchestrator: failed runId={} reason={}",
                    bootStatusTracker.currentRunId(), e.getMessage(), e);
            throw e;
        }
    }

    private BootExecutionObserverPort buildObserver() {
        return new BootExecutionObserverPort() {
            @Override
            public void onRunStarted(String runId, String mode) {
                bootStatusTracker.startRun(runId, mode);
                log.info("bootOrchestrator: start runId={} mode={} phase1Enabled={} phase2Enabled={} phase3Enabled={}",
                        runId, mode,
                        phase1Properties.isEnabled(),
                        phase2Properties.isEnabled(),
                        phase3Properties.isEnabled());
            }

            @Override
            public void onPhaseSkipped(String phase) {
                bootStatusTracker.completePhase(phase, BootPhaseStatus.SKIPPED, "disabled_by_configuration");
                bootMetricsRecorder.recordPhase(phase, BootPhaseStatus.SKIPPED, 0L);
                log.info("bootOrchestrator: phase={} status=SKIPPED reason=disabled_by_configuration runId={}",
                        phase, bootStatusTracker.currentRunId());
            }

            @Override
            public void onPhaseStarted(String phase) {
                bootStatusTracker.startPhase(phase);
            }

            @Override
            public void onPhaseSucceeded(String phase, long durationMs) {
                bootStatusTracker.completePhase(phase, BootPhaseStatus.SUCCESS, "ok");
                bootMetricsRecorder.recordPhase(phase, BootPhaseStatus.SUCCESS, durationMs);
                log.info("bootOrchestrator: phase={} status=SUCCESS durationMs={} runId={}",
                        phase, durationMs, bootStatusTracker.currentRunId());
            }

            @Override
            public void onPhaseFailed(String phase, long durationMs, String message) {
                bootStatusTracker.completePhase(phase, BootPhaseStatus.FAILED, message);
                bootMetricsRecorder.recordPhase(phase, BootPhaseStatus.FAILED, durationMs);
                bootStatusTracker.failRun(phase, message);
            }

            @Override
            public void onPortfolioPhaseEvaluated(String phase, String status, String exchange, String mode, long durationMs) {
                bootMetricsRecorder.recordPortfolioPhaseEvaluation(phase, status, exchange, mode, durationMs);
            }

            @Override
            public void onFailFastRequested(String runId, String phase, String code, String message) {
                bootMetricsRecorder.recordFailFast(phase, code);
                applicationEventPublisher.publishEvent(new BootFailFastEvent(runId, phase, code, message, Instant.now()));
            }

            @Override
            public void onRunCompleted(String runId) {
                bootStatusTracker.completeRun();
                bootMetricsRecorder.recordRun(BootRunStatus.SUCCESS);
                log.info("bootOrchestrator: completed runId={}", runId);
            }

            @Override
            public void onRunFailed(String runId, String failurePhase, String failureMessage) {
                BootRunSnapshot snapshot = bootStatusTracker.snapshot();
                if (snapshot.status() == BootRunStatus.RUNNING) {
                    bootStatusTracker.failRun(
                            failurePhase != null ? failurePhase : "boot",
                            failureMessage);
                }
                bootMetricsRecorder.recordRun(BootRunStatus.FAILED);
            }
        };
    }
}
