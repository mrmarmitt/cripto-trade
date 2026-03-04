package com.marmitt.application.spring.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "runner.boot.phase3")
public class RunnerBootPhase3Properties {

    private boolean enabled = true;
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
