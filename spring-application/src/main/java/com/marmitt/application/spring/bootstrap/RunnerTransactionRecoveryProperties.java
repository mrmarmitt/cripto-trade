package com.marmitt.application.spring.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "runner.recovery.transaction")
public class RunnerTransactionRecoveryProperties {

    private boolean enabled = true;
    private long intervalMs = 60_000L;
    private long staleThresholdMs = 30_000L;
    /**
     * Carencia maior para selecionar PENDING (reserva orfa). Deve exceder com folga o
     * dispatch + ACK (persist-first comita PENDING antes do dispatch; timeout REST ~30s),
     * para nao expirar uma ordem ainda em despacho. Default 10 min.
     */
    private long pendingGraceMs = 600_000L;
    private int maxPerRun = 50;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public void setIntervalMs(long intervalMs) {
        this.intervalMs = intervalMs;
    }

    public long getStaleThresholdMs() {
        return staleThresholdMs;
    }

    public void setStaleThresholdMs(long staleThresholdMs) {
        this.staleThresholdMs = staleThresholdMs;
    }

    public long getPendingGraceMs() {
        return pendingGraceMs;
    }

    public void setPendingGraceMs(long pendingGraceMs) {
        this.pendingGraceMs = pendingGraceMs;
    }

    public int getMaxPerRun() {
        return maxPerRun;
    }

    public void setMaxPerRun(int maxPerRun) {
        this.maxPerRun = maxPerRun;
    }
}
