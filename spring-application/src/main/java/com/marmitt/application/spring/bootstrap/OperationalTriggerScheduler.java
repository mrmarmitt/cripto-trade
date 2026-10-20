package com.marmitt.application.spring.bootstrap;

import com.marmitt.core.ports.inbound.portfolio.EvaluateOperationalTriggersPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara a avaliação periódica dos gatilhos operacionais de Safe Mode (T25). Só agenda; a regra fica
 * no core.
 */
@Slf4j
@Component
public class OperationalTriggerScheduler {

    private final EvaluateOperationalTriggersPort evaluateOperationalTriggers;
    private final AutoSafeModeProperties properties;

    public OperationalTriggerScheduler(EvaluateOperationalTriggersPort evaluateOperationalTriggers,
                                       AutoSafeModeProperties properties) {
        this.evaluateOperationalTriggers = evaluateOperationalTriggers;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${safe-mode.auto.evaluation-interval-ms:30000}",
            initialDelayString = "${safe-mode.auto.evaluation-interval-ms:30000}"
    )
    public void scheduledTick() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            evaluateOperationalTriggers.evaluate();
        } catch (RuntimeException e) {
            log.error("autoSafeMode: evaluation cycle failed - {}", e.getMessage(), e);
        }
    }
}
