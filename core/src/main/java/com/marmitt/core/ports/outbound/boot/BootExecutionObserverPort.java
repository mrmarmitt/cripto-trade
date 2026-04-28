package com.marmitt.core.ports.outbound.boot;

public interface BootExecutionObserverPort {
    void onRunStarted(String runId, String mode);
    void onPhaseSkipped(String phase);
    void onPhaseStarted(String phase);
    void onPhaseSucceeded(String phase, long durationMs);
    void onPhaseFailed(String phase, long durationMs, String message);
    void onPortfolioPhaseEvaluated(String phase, String status, String exchange, String mode, long durationMs);
    void onFailFastRequested(String runId, String phase, String code, String message);
    void onRunCompleted(String runId);
    void onRunFailed(String runId, String failurePhase, String failureMessage);
}
