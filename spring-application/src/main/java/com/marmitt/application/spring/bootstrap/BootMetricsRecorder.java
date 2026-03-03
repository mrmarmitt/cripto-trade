package com.marmitt.application.spring.bootstrap;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class BootMetricsRecorder {

    private final MeterRegistry meterRegistry;

    public BootMetricsRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordRun(BootRunStatus status) {
        meterRegistry.counter("boot.run.total", "status", status.name()).increment();
    }

    public void recordPhase(String phase, BootPhaseStatus status, long durationMs) {
        meterRegistry.counter("boot.phase.total", "phase", phase, "status", status.name()).increment();
        Timer.builder("boot.phase.duration")
                .tag("phase", phase)
                .tag("status", status.name())
                .register(meterRegistry)
                .record(Duration.ofMillis(Math.max(0L, durationMs)));
    }

    public void recordFailFast(String phase, String code) {
        meterRegistry.counter("boot.failfast.total", "phase", phase, "code", code).increment();
    }
}
