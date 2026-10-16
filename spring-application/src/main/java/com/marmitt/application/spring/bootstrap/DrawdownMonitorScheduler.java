package com.marmitt.application.spring.bootstrap;

import com.marmitt.core.ports.inbound.portfolio.EvaluateDrawdownPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara a avaliação periódica do drawdown (T43). Só agenda; a regra fica no core.
 */
@Slf4j
@Component
public class DrawdownMonitorScheduler {

    private final EvaluateDrawdownPort evaluateDrawdown;
    private final CircuitBreakerProperties properties;

    public DrawdownMonitorScheduler(EvaluateDrawdownPort evaluateDrawdown, CircuitBreakerProperties properties) {
        this.evaluateDrawdown = evaluateDrawdown;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${portfolio.circuit-breaker.check-interval-ms:10000}",
            initialDelayString = "${portfolio.circuit-breaker.check-interval-ms:10000}"
    )
    public void scheduledTick() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            evaluateDrawdown.evaluate();
        } catch (RuntimeException e) {
            log.error("drawdown: monitor cycle failed - {}", e.getMessage(), e);
        }
    }
}
