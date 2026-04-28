package com.marmitt.application.spring.bootstrap;

import com.marmitt.core.enums.BootAccountQueryPolicy;
import com.marmitt.core.enums.BootFailureMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "runner.boot.phase2")
public class RunnerBootPhase2Properties {

    private boolean enabled = true;
    private BootFailureMode mode = BootFailureMode.WARN_ONLY;
    private BootAccountQueryPolicy accountQueryPolicy = BootAccountQueryPolicy.SKIP;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public BootFailureMode getMode() {
        return mode;
    }

    public void setMode(BootFailureMode mode) {
        this.mode = mode;
    }

    public BootAccountQueryPolicy getAccountQueryPolicy() {
        return accountQueryPolicy;
    }

    public void setAccountQueryPolicy(BootAccountQueryPolicy accountQueryPolicy) {
        this.accountQueryPolicy = accountQueryPolicy;
    }
}
