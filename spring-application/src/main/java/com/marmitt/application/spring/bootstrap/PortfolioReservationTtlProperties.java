package com.marmitt.application.spring.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "runner.boot.phase2.portfolio.reservation-ttl")
public class PortfolioReservationTtlProperties {

    private boolean enabled = true;
    private long ttlMs = 300_000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getTtlMs() {
        return ttlMs;
    }

    public void setTtlMs(long ttlMs) {
        this.ttlMs = ttlMs;
    }
}
