package com.marmitt.application.spring.metrics;

import com.marmitt.core.ports.outbound.metrics.SignalDecision;
import com.marmitt.core.ports.outbound.metrics.SignalMetricsPort;
import com.marmitt.core.ports.outbound.metrics.TickDiscardReason;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Implementacao Micrometer da {@link SignalMetricsPort} (T23 G2).
 *
 * <p>Emite o counter {@code signal.evaluated.total} com tags {@code runnerId} e
 * {@code decision}. Mantem a dependencia de Micrometer fora do core, espelhando o
 * padrao de {@code BootMetricsRecorder}.
 */
@Component
public class MicrometerSignalMetricsAdapter implements SignalMetricsPort {

    static final String METRIC = "signal.evaluated.total";
    static final String TICKS_DISCARDED = "runner.ticks.discarded";
    static final String PROCESSING_TIME = "runner.signal.processing.time";

    private final MeterRegistry meterRegistry;

    public MicrometerSignalMetricsAdapter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void recordSignalEvaluated(UUID runnerId, SignalDecision decision) {
        meterRegistry.counter(METRIC,
                "runnerId", runnerId != null ? runnerId.toString() : "UNKNOWN",
                "decision", decision.name()
        ).increment();
    }

    @Override
    public void recordTickDiscarded(UUID runnerId, TickDiscardReason reason) {
        meterRegistry.counter(TICKS_DISCARDED, "runnerId", tag(runnerId), "reason", reason.name()).increment();
    }

    @Override
    public void recordProcessingTime(UUID runnerId, Duration duration) {
        meterRegistry.timer(PROCESSING_TIME, "runnerId", tag(runnerId)).record(duration);
    }

    private static String tag(UUID runnerId) {
        return runnerId != null ? runnerId.toString() : "UNKNOWN";
    }
}
