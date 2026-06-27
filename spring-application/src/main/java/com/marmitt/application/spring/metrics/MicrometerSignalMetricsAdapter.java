package com.marmitt.application.spring.metrics;

import com.marmitt.core.ports.outbound.metrics.SignalDecision;
import com.marmitt.core.ports.outbound.metrics.SignalMetricsPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

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
}
