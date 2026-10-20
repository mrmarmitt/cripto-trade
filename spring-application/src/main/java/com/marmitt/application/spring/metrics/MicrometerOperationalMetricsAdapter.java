package com.marmitt.application.spring.metrics;

import com.marmitt.core.dto.operations.OperationalStateReport;
import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.enums.SafeModeStatus;
import com.marmitt.core.ports.outbound.metrics.OperationalMetricsPort;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Métricas de estado operacional (T51; IG §7.7, §11.7, §13.7). Gauges guardam o último valor amostrado
 * pelo {@code ReportOperationalStateUseCase}; contador e timer de lifecycle são incrementados nas
 * transições.
 */
@Component
public class MicrometerOperationalMetricsAdapter implements OperationalMetricsPort {

    static final String SAFE_MODE_LEVEL = "circuit_breaker.current_level";
    static final String SAFE_MODE_ACTIVE_SECONDS = "circuit_breaker.duration.active.seconds";
    static final String RUNNERS_SUSPENDED = "circuit_breaker.runner.suspended.count";
    static final String GLOBAL_UTILIZATION = "capital.utilization.global";
    static final String RUNNER_UTILIZATION = "capital.utilization.runner";
    static final String OLDEST_RESERVATION_SECONDS = "capital.reservation.oldest.age.seconds";
    static final String RUNNERS_BY_STATE = "runner.lifecycle.current_state";
    static final String TERMINATION_STUCK = "runner.lifecycle.termination.stuck";
    static final String TRANSITIONS = "runner.lifecycle.transitions.total";
    static final String TERMINATING_DURATION = "runner.lifecycle.terminating.duration";

    /** T55: 0=OK, 1=WARN, 2=CRITICAL. */
    static final String OPERATIONAL_STATE = "operational_state_status";
    static final String OPERATIONAL_CHECK = "operational_check_status";

    private final MeterRegistry meterRegistry;
    private final Map<String, AtomicReference<Double>> gauges = new ConcurrentHashMap<>();

    public MicrometerOperationalMetricsAdapter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void recordPortfolioState(UUID portfolioId, SafeModeStatus level, Duration safeModeActiveFor,
                                     BigDecimal globalUtilization, long haltedRunners) {
        Tags tags = Tags.of("portfolioId", portfolioId.toString());
        // IG §11.7: 0=NORMAL, 1=HALT, 2=CANCEL_ALL, 3=PANIC_SELL (ordem do enum).
        gauge(SAFE_MODE_LEVEL, tags).set((double) level.ordinal());
        gauge(SAFE_MODE_ACTIVE_SECONDS, tags).set((double) safeModeActiveFor.toSeconds());
        gauge(GLOBAL_UTILIZATION, tags).set(globalUtilization.doubleValue());
        gauge(RUNNERS_SUSPENDED, tags).set((double) haltedRunners);
    }

    @Override
    public void recordRunnerState(UUID runnerId, BigDecimal capitalUtilization, Duration oldestInFlightAge) {
        Tags tags = Tags.of("runnerId", runnerId.toString());
        gauge(RUNNER_UTILIZATION, tags).set(capitalUtilization.doubleValue());
        gauge(OLDEST_RESERVATION_SECONDS, tags).set((double) oldestInFlightAge.toSeconds());
    }

    @Override
    public void recordRunnerCounts(Map<RunnerStatus, Long> runnersByStatus, long terminationStuck) {
        runnersByStatus.forEach((status, count) ->
                gauge(RUNNERS_BY_STATE, Tags.of("status", status.name())).set(count.doubleValue()));
        gauge(TERMINATION_STUCK, Tags.empty()).set((double) terminationStuck);
    }

    @Override
    public void recordTransition(RunnerStatus from, RunnerStatus to) {
        meterRegistry.counter(TRANSITIONS, "from", from == null ? "NONE" : from.name(), "to", to.name())
                .increment();
    }

    @Override
    public void recordTerminatingDuration(Duration duration) {
        Timer.builder(TERMINATING_DURATION).register(meterRegistry).record(duration);
    }

    @Override
    public void recordOperationalState(OperationalStateReport report) {
        gauge(OPERATIONAL_STATE, Tags.empty()).set((double) report.status().ordinal());
        report.checks().forEach(check ->
                gauge(OPERATIONAL_CHECK, Tags.of("check", check.id())).set((double) check.status().ordinal()));
    }

    private AtomicReference<Double> gauge(String name, Tags tags) {
        return gauges.computeIfAbsent(name + tags, key -> {
            AtomicReference<Double> holder = new AtomicReference<>(0.0);
            Gauge.builder(name, holder, AtomicReference::get).tags(tags).register(meterRegistry);
            return holder;
        });
    }
}
