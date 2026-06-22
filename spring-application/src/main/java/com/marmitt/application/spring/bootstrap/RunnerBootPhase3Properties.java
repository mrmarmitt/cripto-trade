package com.marmitt.application.spring.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "runner.boot.phase3")
public class RunnerBootPhase3Properties {

    private boolean enabled = true;
    /**
     * Carencia para reconciliar PENDING no boot. Um PENDING mais novo que isto e DEFERIDO
     * (mantido PENDING) em vez de consultado/expirado, evitando expirar uma ordem que foi
     * enviada logo antes do crash mas ainda nao esta visivel na query da exchange. O watchdog
     * de runtime cuida dele depois (com sua propria carencia). Default 10 min.
     */
    private long pendingGraceMs = 600_000L;
    private long exchangeQueryTimeoutMs = 10_000L;
    private int exchangeQueryMaxAttempts = 3;
    private long exchangeQueryInitialBackoffMs = 300L;
    private double exchangeQueryBackoffMultiplier = 2.0d;
    private long exchangeQueryMaxBackoffMs = 5_000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getPendingGraceMs() {
        return pendingGraceMs;
    }

    public void setPendingGraceMs(long pendingGraceMs) {
        this.pendingGraceMs = pendingGraceMs;
    }

    public long getExchangeQueryTimeoutMs() {
        return exchangeQueryTimeoutMs;
    }

    public void setExchangeQueryTimeoutMs(long exchangeQueryTimeoutMs) {
        this.exchangeQueryTimeoutMs = exchangeQueryTimeoutMs;
    }

    public int getExchangeQueryMaxAttempts() {
        return exchangeQueryMaxAttempts;
    }

    public void setExchangeQueryMaxAttempts(int exchangeQueryMaxAttempts) {
        this.exchangeQueryMaxAttempts = exchangeQueryMaxAttempts;
    }

    public long getExchangeQueryInitialBackoffMs() {
        return exchangeQueryInitialBackoffMs;
    }

    public void setExchangeQueryInitialBackoffMs(long exchangeQueryInitialBackoffMs) {
        this.exchangeQueryInitialBackoffMs = exchangeQueryInitialBackoffMs;
    }

    public double getExchangeQueryBackoffMultiplier() {
        return exchangeQueryBackoffMultiplier;
    }

    public void setExchangeQueryBackoffMultiplier(double exchangeQueryBackoffMultiplier) {
        this.exchangeQueryBackoffMultiplier = exchangeQueryBackoffMultiplier;
    }

    public long getExchangeQueryMaxBackoffMs() {
        return exchangeQueryMaxBackoffMs;
    }

    public void setExchangeQueryMaxBackoffMs(long exchangeQueryMaxBackoffMs) {
        this.exchangeQueryMaxBackoffMs = exchangeQueryMaxBackoffMs;
    }
}
