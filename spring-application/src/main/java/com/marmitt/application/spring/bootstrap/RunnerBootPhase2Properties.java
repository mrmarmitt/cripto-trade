package com.marmitt.application.spring.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "runner.boot.phase2")
public class RunnerBootPhase2Properties {

    private boolean enabled = true;
    private Phase2Mode mode = Phase2Mode.WARN_ONLY;
    private Phase2AccountQueryPolicy accountQueryPolicy = Phase2AccountQueryPolicy.SKIP;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Phase2Mode getMode() {
        return mode;
    }

    public void setMode(Phase2Mode mode) {
        this.mode = mode;
    }

    public Phase2AccountQueryPolicy getAccountQueryPolicy() {
        return accountQueryPolicy;
    }

    public void setAccountQueryPolicy(Phase2AccountQueryPolicy accountQueryPolicy) {
        this.accountQueryPolicy = accountQueryPolicy;
    }
}
