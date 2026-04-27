package com.marmitt.application.spring.bootstrap;

import com.marmitt.core.application.usecase.boot.BootFailFastException;
import com.marmitt.core.application.usecase.boot.BootPhaseExecutionException;
import com.marmitt.core.application.usecase.boot.RunBootSequenceUseCase;
import com.marmitt.core.dto.boot.BootExecutionCommand;
import com.marmitt.core.dto.boot.BootRunSnapshot;
import com.marmitt.core.dto.boot.BootExecutionSummary;
import com.marmitt.core.enums.BootPhaseStatus;
import com.marmitt.core.enums.BootRunStatus;
import com.marmitt.core.enums.BootAccountQueryPolicy;
import com.marmitt.core.enums.BootFailureMode;
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
    private final RunBootSequenceUseCase runBootSequenceUseCase;
    private final BootStatusTracker bootStatusTracker;
    private final BootMetricsRecorder bootMetricsRecorder;
    private final ApplicationEventPublisher applicationEventPublisher;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        bootStatusTracker.startRun(phase2Properties.getMode().name());
        log.info("bootOrchestrator: start runId={} mode={} phase1Enabled={} phase2Enabled={} phase3Enabled={}",
                bootStatusTracker.currentRunId(),
                phase2Properties.getMode(),
                phase1Properties.isEnabled(),
                phase2Properties.isEnabled(),
                phase3Properties.isEnabled());

        try {
            BootExecutionSummary summary = runBootSequenceUseCase.execute(buildCommand(), new SpringBootExecutionObserver());

            bootStatusTracker.completeRun();
            bootMetricsRecorder.recordRun(BootRunStatus.SUCCESS);
            log.info("bootOrchestrator: completed runId={} portfolios={} runners={}",
                    bootStatusTracker.currentRunId(),
                    summary.portfoliosCount(),
                    summary.runnerSummaries().size());
        } catch (BootFailFastException e) {
            failRunIfStillRunning(e.phase(), e.getMessage());
            bootMetricsRecorder.recordRun(BootRunStatus.FAILED);
            log.error("bootOrchestrator: failed runId={} phase={} code={} reason={}",
                    bootStatusTracker.currentRunId(), e.phase(), e.code(), e.getMessage(), e);
            throw new IllegalStateException(e.getMessage(), e);
        } catch (BootPhaseExecutionException e) {
            failRunIfStillRunning(e.phase(), e.getMessage());
            bootMetricsRecorder.recordRun(BootRunStatus.FAILED);
            log.error("bootOrchestrator: failed runId={} phase={} reason={}",
                    bootStatusTracker.currentRunId(), e.phase(), e.getMessage(), e);
            throw new IllegalStateException(e.getMessage(), e);
        } catch (RuntimeException e) {
            failRunIfStillRunning("boot", e.getMessage());
            bootMetricsRecorder.recordRun(BootRunStatus.FAILED);
            log.error("bootOrchestrator: failed runId={} reason={}",
                    bootStatusTracker.currentRunId(), e.getMessage(), e);
            throw e;
        }
    }

    private BootExecutionCommand buildCommand() {
        return new BootExecutionCommand(
                phase1Properties.isEnabled(),
                phase2Properties.isEnabled(),
                phase3Properties.isEnabled(),
                phase2Properties.getMode(),
                phase2Properties.getAccountQueryPolicy(),
                portfolioSanityCheckProperties.isEnabled(),
                portfolioSanityCheckProperties.getThreshold(),
                portfolioZombieDetectionProperties.isEnabled(),
                portfolioReservationTtlProperties.isEnabled(),
                portfolioReservationTtlProperties.getTtlMs(),
                portfolioCutoffProperties.isEnabled()
        );
    }

    private void failRunIfStillRunning(String phase, String message) {
        BootRunSnapshot snapshot = bootStatusTracker.snapshot();
        if (snapshot.status() == BootRunStatus.RUNNING) {
            bootStatusTracker.failRun(phase, message);
        }
    }

    private final class SpringBootExecutionObserver implements BootExecutionObserverPort {

        @Override
        public void onPhaseStarted(String phase) {
            bootStatusTracker.startPhase(phase);
        }

        @Override
        public void onPhaseCompleted(String phase, boolean success, long durationMs, String message) {
            BootPhaseStatus status = success ? BootPhaseStatus.SUCCESS : BootPhaseStatus.FAILED;
            bootStatusTracker.completePhase(phase, status, message);
            bootMetricsRecorder.recordPhase(phase, status, durationMs);
            log.info("bootOrchestrator: phase={} status={} durationMs={} runId={}",
                    phase, status, durationMs, bootStatusTracker.currentRunId());
        }

        @Override
        public void onPhaseSkipped(String phase, String reason) {
            bootStatusTracker.completePhase(phase, BootPhaseStatus.SKIPPED, reason);
            bootMetricsRecorder.recordPhase(phase, BootPhaseStatus.SKIPPED, 0L);
            log.info("bootOrchestrator: phase={} status=SKIPPED reason={} runId={}",
                    phase, reason, bootStatusTracker.currentRunId());
        }

        @Override
        public void onPortfolioPhaseEvaluated(String phase, String status, String exchange, String mode, long durationMs) {
            bootMetricsRecorder.recordPortfolioPhaseEvaluation(phase, status, exchange, mode, durationMs);
        }

        @Override
        public void onFailFastRequested(String phase, String code, String message) {
            bootMetricsRecorder.recordFailFast(phase, code);
            applicationEventPublisher.publishEvent(new BootFailFastEvent(
                    bootStatusTracker.currentRunId(),
                    phase,
                    code,
                    message,
                    Instant.now()
            ));
        }
    }
}
