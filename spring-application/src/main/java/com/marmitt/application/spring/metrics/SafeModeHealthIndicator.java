package com.marmitt.application.spring.metrics;

import com.marmitt.core.enums.SafeModeStatus;
import com.marmitt.core.ports.inbound.portfolio.ReportOperationalStatePort;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Nível do Safe Mode por portfolio no {@code /actuator/health} (T51, IG §11.4.3). O status segue UP com
 * Safe Mode ativo: é um estado de proteção, não falha da aplicação — derrubar o health faria um
 * orquestrador reiniciar o processo no meio da liquidação. O alerta vem da métrica
 * {@code circuit_breaker.current_level > 0}.
 */
@Component("safeMode")
public class SafeModeHealthIndicator implements HealthIndicator {

    private final ReportOperationalStatePort reportOperationalState;

    public SafeModeHealthIndicator(ReportOperationalStatePort reportOperationalState) {
        this.reportOperationalState = reportOperationalState;
    }

    @Override
    public Health health() {
        Map<UUID, SafeModeStatus> levels = reportOperationalState.safeModeByPortfolio();
        Map<String, String> portfolios = new TreeMap<>();
        levels.forEach((portfolioId, level) -> portfolios.put(portfolioId.toString(), level.name()));
        return Health.up()
                .withDetail("active", levels.values().stream().anyMatch(SafeModeStatus::isActive))
                .withDetail("portfolios", portfolios)
                .build();
    }
}
