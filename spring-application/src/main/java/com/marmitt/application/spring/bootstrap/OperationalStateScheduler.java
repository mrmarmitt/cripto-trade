package com.marmitt.application.spring.bootstrap;

import com.marmitt.core.ports.inbound.operations.InspectOperationalStatePort;
import com.marmitt.core.ports.outbound.metrics.OperationalMetricsPort;
import com.marmitt.core.ports.inbound.portfolio.ReportOperationalStatePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara a amostra periódica do estado operacional (T51) e do estado consolidado (T55). Só agenda; as
 * contas ficam no core.
 */
@Slf4j
@Component
public class OperationalStateScheduler {

    private final ReportOperationalStatePort reportOperationalState;
    private final InspectOperationalStatePort inspectOperationalState;
    private final OperationalMetricsPort operationalMetrics;
    private final OperationalStateProperties properties;

    public OperationalStateScheduler(ReportOperationalStatePort reportOperationalState,
                                     InspectOperationalStatePort inspectOperationalState,
                                     OperationalMetricsPort operationalMetrics,
                                     OperationalStateProperties properties) {
        this.reportOperationalState = reportOperationalState;
        this.inspectOperationalState = inspectOperationalState;
        this.operationalMetrics = operationalMetrics;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${ctrade.observability.report-interval-ms:15000}",
            initialDelayString = "${ctrade.observability.report-interval-ms:15000}"
    )
    public void scheduledTick() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            reportOperationalState.report();
        } catch (RuntimeException e) {
            log.error("observability: operational state report failed - {}", e.getMessage(), e);
        }
        // T55: o estado consolidado vira métrica para alerta no Grafana.
        try {
            operationalMetrics.recordOperationalState(inspectOperationalState.inspect());
        } catch (RuntimeException e) {
            log.error("observability: operational state inspection failed - {}", e.getMessage(), e);
        }
    }
}
