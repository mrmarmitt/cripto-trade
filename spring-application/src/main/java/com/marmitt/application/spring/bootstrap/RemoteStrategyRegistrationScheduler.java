package com.marmitt.application.spring.bootstrap;

import com.marmitt.application.spring.config.strategy.RemoteStrategyConfiguration.RemoteStrategyCatalog;
import com.marmitt.core.ports.inbound.strategy.RegisterRemoteStrategiesPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Registra as estratégias remotas do catálogo no boot e tenta de novo periodicamente as que ainda
 * não foram registradas (serviço que subiu depois do ctrade). Só agenda; a validação é do core.
 */
@Slf4j
@Component
public class RemoteStrategyRegistrationScheduler {

    private final RegisterRemoteStrategiesPort registerRemoteStrategies;
    private final RemoteStrategyCatalog catalog;

    public RemoteStrategyRegistrationScheduler(RegisterRemoteStrategiesPort registerRemoteStrategies,
                                               RemoteStrategyCatalog catalog) {
        this.registerRemoteStrategies = registerRemoteStrategies;
        this.catalog = catalog;
    }

    @Scheduled(initialDelay = 0,
            fixedDelayString = "${ctrade.strategy.remote.registration-retry-interval-ms:30000}")
    public void registerPending() {
        if (catalog.definitions().isEmpty()) {
            return;
        }
        try {
            registerRemoteStrategies.registerPending(catalog.definitions());
        } catch (RuntimeException e) {
            log.error("remoteStrategy: registration cycle failed - {}", e.getMessage(), e);
        }
    }
}
