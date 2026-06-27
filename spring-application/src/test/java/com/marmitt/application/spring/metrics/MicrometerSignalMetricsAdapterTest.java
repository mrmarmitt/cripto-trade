package com.marmitt.application.spring.metrics;

import com.marmitt.core.ports.outbound.metrics.SignalDecision;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MicrometerSignalMetricsAdapterTest {

    private SimpleMeterRegistry registry;
    private MicrometerSignalMetricsAdapter adapter;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        adapter = new MicrometerSignalMetricsAdapter(registry);
    }

    @Test
    void recordsCounterWithRunnerAndDecisionTags() {
        UUID runnerId = UUID.randomUUID();

        adapter.recordSignalEvaluated(runnerId, SignalDecision.HOLD);
        adapter.recordSignalEvaluated(runnerId, SignalDecision.HOLD);
        adapter.recordSignalEvaluated(runnerId, SignalDecision.BUY);

        assertEquals(2.0, counterCount(runnerId.toString(), "HOLD"));
        assertEquals(1.0, counterCount(runnerId.toString(), "BUY"));
    }

    @Test
    void keepsSeparateSeriesPerRunner() {
        UUID runnerA = UUID.randomUUID();
        UUID runnerB = UUID.randomUUID();

        adapter.recordSignalEvaluated(runnerA, SignalDecision.REJECTED_CAPITAL);
        adapter.recordSignalEvaluated(runnerB, SignalDecision.REJECTED_LOCK);

        assertEquals(1.0, counterCount(runnerA.toString(), "REJECTED_CAPITAL"));
        assertEquals(1.0, counterCount(runnerB.toString(), "REJECTED_LOCK"));
        assertNull(registry.find(MicrometerSignalMetricsAdapter.METRIC)
                .tags("runnerId", runnerA.toString(), "decision", "REJECTED_LOCK")
                .counter());
    }

    @Test
    void usesPlaceholderWhenRunnerIdIsNull() {
        adapter.recordSignalEvaluated(null, SignalDecision.CANCEL);

        assertEquals(1.0, counterCount("UNKNOWN", "CANCEL"));
    }

    private double counterCount(String runnerId, String decision) {
        return registry.get(MicrometerSignalMetricsAdapter.METRIC)
                .tags("runnerId", runnerId, "decision", decision)
                .counter()
                .count();
    }
}
