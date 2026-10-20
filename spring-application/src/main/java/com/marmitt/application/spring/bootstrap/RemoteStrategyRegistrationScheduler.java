package com.marmitt.application.spring.bootstrap;

import com.marmitt.core.ports.inbound.strategy.SyncRemoteStrategiesPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Alinha as estratégias em uso com o catálogo persistido no boot e periodicamente (T54): registra as
 * entradas no repositório de estratégias e repete a conferência das habilitadas que ainda não passaram
 * (serviço que subiu depois do ctrade). Só agenda; a regra é do core.
 */
@Slf4j
@Component
public class RemoteStrategyRegistrationScheduler {

    private final SyncRemoteStrategiesPort syncRemoteStrategies;

    public RemoteStrategyRegistrationScheduler(SyncRemoteStrategiesPort syncRemoteStrategies) {
        this.syncRemoteStrategies = syncRemoteStrategies;
    }

    @Scheduled(initialDelay = 0,
            fixedDelayString = "${ctrade.strategy.remote.registration-retry-interval-ms:30000}")
    public void registerPending() {
        try {
            syncRemoteStrategies.syncAll();
        } catch (RuntimeException e) {
            log.error("remoteStrategy: sync cycle failed - {}", e.getMessage(), e);
        }
    }
}
