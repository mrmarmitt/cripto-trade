package com.marmitt.core.ports.outbound.boot;

public interface BootExecutionObserverPort {

    void onPhaseStarted(String phase);

    void onPhaseCompleted(String phase, boolean success, long durationMs, String message);

    void onPhaseSkipped(String phase, String reason);

    void onPortfolioPhaseEvaluated(String phase, String status, String exchange, String mode, long durationMs);

    void onFailFastRequested(String phase, String code, String message);
}
