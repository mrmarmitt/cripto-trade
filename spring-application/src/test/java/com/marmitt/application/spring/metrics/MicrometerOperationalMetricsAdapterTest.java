package com.marmitt.application.spring.metrics;

import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.enums.SafeModeStatus;
import com.marmitt.core.ports.inbound.portfolio.ReportOperationalStatePort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T51: gauges, contador e timer de estado operacional, e o nivel do Safe Mode no health.
 */
class MicrometerOperationalMetricsAdapterTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerOperationalMetricsAdapter adapter = new MicrometerOperationalMetricsAdapter(registry);

    @Test
    void portfolioGaugesKeepTheLastSample() {
        UUID portfolioId = UUID.randomUUID();
        adapter.recordPortfolioState(portfolioId, SafeModeStatus.CANCEL_ALL, Duration.ofSeconds(42),
                new BigDecimal("0.35"), 2);
        adapter.recordPortfolioState(portfolioId, SafeModeStatus.PANIC_SELL, Duration.ofSeconds(50),
                new BigDecimal("0.10"), 1);

        assertEquals(3.0, gauge(MicrometerOperationalMetricsAdapter.SAFE_MODE_LEVEL, "portfolioId", portfolioId));
        assertEquals(50.0, gauge(MicrometerOperationalMetricsAdapter.SAFE_MODE_ACTIVE_SECONDS, "portfolioId", portfolioId));
        assertEquals(0.10, gauge(MicrometerOperationalMetricsAdapter.GLOBAL_UTILIZATION, "portfolioId", portfolioId));
        assertEquals(1.0, gauge(MicrometerOperationalMetricsAdapter.RUNNERS_SUSPENDED, "portfolioId", portfolioId));
    }

    @Test
    void runnerGaugesAndLifecycleCounters() {
        UUID runnerId = UUID.randomUUID();
        adapter.recordRunnerState(runnerId, new BigDecimal("0.4"), Duration.ofSeconds(400));
        adapter.recordRunnerCounts(Map.of(RunnerStatus.ACTIVE, 3L, RunnerStatus.TERMINATING, 1L), 1);
        adapter.recordTransition(null, RunnerStatus.CREATED);
        adapter.recordTransition(RunnerStatus.ACTIVE, RunnerStatus.HALTED);
        adapter.recordTransition(RunnerStatus.ACTIVE, RunnerStatus.HALTED);
        adapter.recordTerminatingDuration(Duration.ofMinutes(3));

        assertEquals(0.4, gauge(MicrometerOperationalMetricsAdapter.RUNNER_UTILIZATION, "runnerId", runnerId));
        assertEquals(400.0, gauge(MicrometerOperationalMetricsAdapter.OLDEST_RESERVATION_SECONDS, "runnerId", runnerId));
        assertEquals(3.0, registry.get(MicrometerOperationalMetricsAdapter.RUNNERS_BY_STATE)
                .tag("status", "ACTIVE").gauge().value());
        assertEquals(1.0, registry.get(MicrometerOperationalMetricsAdapter.TERMINATION_STUCK).gauge().value());
        assertEquals(1.0, registry.get(MicrometerOperationalMetricsAdapter.TRANSITIONS)
                .tags("from", "NONE", "to", "CREATED").counter().count());
        assertEquals(2.0, registry.get(MicrometerOperationalMetricsAdapter.TRANSITIONS)
                .tags("from", "ACTIVE", "to", "HALTED").counter().count());
        assertEquals(1L, registry.get(MicrometerOperationalMetricsAdapter.TERMINATING_DURATION).timer().count());
    }

    @Test
    void healthShowsTheSafeModeLevelPerPortfolioAndStaysUp() {
        UUID calm = UUID.randomUUID();
        UUID panicking = UUID.randomUUID();
        ReportOperationalStatePort report = mock(ReportOperationalStatePort.class);
        when(report.safeModeByPortfolio()).thenReturn(Map.of(calm, SafeModeStatus.NORMAL,
                panicking, SafeModeStatus.PANIC_SELL));

        Health health = new SafeModeHealthIndicator(report).health();

        assertEquals(Status.UP, health.getStatus(), "Safe Mode e protecao, nao falha da aplicacao");
        assertEquals(true, health.getDetails().get("active"));
        assertEquals(Map.of(calm.toString(), "NORMAL", panicking.toString(), "PANIC_SELL"),
                health.getDetails().get("portfolios"));
    }

    private double gauge(String name, String tag, UUID id) {
        return registry.get(name).tag(tag, id.toString()).gauge().value();
    }
}
