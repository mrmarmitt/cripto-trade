package com.marmitt.application.spring.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "runner.boot.phase2")
public class RunnerBootPhase2Properties {

    private Phase2Mode mode = Phase2Mode.WARN_ONLY;
    private BigDecimal absoluteTolerance = new BigDecimal("0.00010000");
    private BigDecimal percentTolerance = new BigDecimal("0.10");

    public Phase2Mode getMode() {
        return mode;
    }

    public void setMode(Phase2Mode mode) {
        this.mode = mode;
    }

    public BigDecimal getAbsoluteTolerance() {
        return absoluteTolerance;
    }

    public void setAbsoluteTolerance(BigDecimal absoluteTolerance) {
        this.absoluteTolerance = absoluteTolerance;
    }

    public BigDecimal getPercentTolerance() {
        return percentTolerance;
    }

    public void setPercentTolerance(BigDecimal percentTolerance) {
        this.percentTolerance = percentTolerance;
    }
}
