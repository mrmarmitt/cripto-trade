package com.marmitt.application.spring.bootstrap;

import com.marmitt.core.dto.boot.BootPhaseSnapshot;
import com.marmitt.core.dto.boot.BootRunSnapshot;
import com.marmitt.core.enums.BootPhaseStatus;
import com.marmitt.core.enums.BootRunStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class BootStatusTracker {

    private String runId;
    private BootRunStatus runStatus = BootRunStatus.NOT_STARTED;
    private String mode;
    private Instant runStartedAt;
    private Instant runFinishedAt;
    private Long runDurationMs;
    private String failurePhase;
    private String failureMessage;
    private final Map<String, PhaseMutable> phases = new LinkedHashMap<>();

    public synchronized void startRun(String runId, String mode) {
        this.runId = runId;
        this.runStatus = BootRunStatus.RUNNING;
        this.mode = mode;
        this.runStartedAt = Instant.now();
        this.runFinishedAt = null;
        this.runDurationMs = null;
        this.failurePhase = null;
        this.failureMessage = null;
        this.phases.clear();
    }

    public synchronized void completeRun() {
        this.runStatus = BootRunStatus.SUCCESS;
        this.runFinishedAt = Instant.now();
        this.runDurationMs = durationMs(runStartedAt, runFinishedAt);
    }

    public synchronized void failRun(String phase, String message) {
        this.runStatus = BootRunStatus.FAILED;
        this.failurePhase = phase;
        this.failureMessage = message;
        this.runFinishedAt = Instant.now();
        this.runDurationMs = durationMs(runStartedAt, runFinishedAt);
    }

    public synchronized void startPhase(String phase) {
        PhaseMutable mutable = phases.computeIfAbsent(phase, PhaseMutable::new);
        mutable.status = BootPhaseStatus.RUNNING;
        mutable.startedAt = Instant.now();
        mutable.finishedAt = null;
        mutable.durationMs = null;
        mutable.message = null;
    }

    public synchronized void completePhase(String phase, BootPhaseStatus status, String message) {
        PhaseMutable mutable = phases.computeIfAbsent(phase, PhaseMutable::new);
        if (mutable.startedAt == null) {
            mutable.startedAt = Instant.now();
        }
        mutable.status = status;
        mutable.finishedAt = Instant.now();
        mutable.durationMs = durationMs(mutable.startedAt, mutable.finishedAt);
        mutable.message = message;
    }

    public synchronized BootRunSnapshot snapshot() {
        List<BootPhaseSnapshot> phaseSnapshots = new ArrayList<>();
        for (PhaseMutable phase : phases.values()) {
            phaseSnapshots.add(new BootPhaseSnapshot(
                    phase.phase,
                    phase.status,
                    phase.startedAt,
                    phase.finishedAt,
                    phase.durationMs,
                    phase.message
            ));
        }
        return new BootRunSnapshot(
                runId,
                runStatus,
                mode,
                runStartedAt,
                runFinishedAt,
                runDurationMs,
                failurePhase,
                failureMessage,
                List.copyOf(phaseSnapshots)
        );
    }

    public synchronized String currentRunId() {
        return runId;
    }

    private static long durationMs(Instant start, Instant end) {
        if (start == null || end == null) {
            return 0L;
        }
        return Duration.between(start, end).toMillis();
    }

    private static final class PhaseMutable {
        private final String phase;
        private BootPhaseStatus status = BootPhaseStatus.SKIPPED;
        private Instant startedAt;
        private Instant finishedAt;
        private Long durationMs;
        private String message;

        private PhaseMutable(String phase) {
            this.phase = phase;
        }
    }
}
