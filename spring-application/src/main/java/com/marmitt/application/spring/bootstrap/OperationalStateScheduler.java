package com.marmitt.application.spring.bootstrap;

import com.marmitt.core.ports.inbound.portfolio.ReportOperationalStatePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara a amostra periódica do estado operacional (T51). Só agenda; as contas ficam no core.
 */
@Slf4j
@Component
public class OperationalStateScheduler {

    private final ReportOperationalStatePort reportOperationalState;
    private final OperationalStateProperties properties;

    public OperationalStateScheduler(ReportOperationalStatePort reportOperationalState,
                                     OperationalStateProperties properties) {
        this.reportOperationalState = reportOperationalState;
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
    }
}
