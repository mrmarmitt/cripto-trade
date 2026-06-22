package com.marmitt.application.spring.bootstrap;

import com.marmitt.core.dto.runner.request.RecoverStaleTransactionsRequest;
import com.marmitt.core.dto.runner.response.RecoverStaleTransactionsResponse;
import com.marmitt.core.ports.inbound.runner.RecoverStaleTransactionsPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
public class RunnerTransactionRecoveryWatchdog {

    private final RecoverStaleTransactionsPort recoverStaleTransactionsPort;
    private final RunnerTransactionRecoveryProperties properties;

    public RunnerTransactionRecoveryWatchdog(RecoverStaleTransactionsPort recoverStaleTransactionsPort,
                                             RunnerTransactionRecoveryProperties properties) {
        this.recoverStaleTransactionsPort = recoverStaleTransactionsPort;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${runner.recovery.transaction.interval-ms:60000}",
            initialDelayString = "${runner.recovery.transaction.interval-ms:60000}"
    )
    public void scheduledTick() {
        runRecoveryCycle();
    }

    public RecoverStaleTransactionsResponse runRecoveryCycle() {
        if (!properties.isEnabled()) {
            return new RecoverStaleTransactionsResponse(0, 0, 0, 0, 0);
        }

        long staleThresholdMs = Math.max(0L, properties.getStaleThresholdMs());
        // PENDING (reserva orfa) exige carencia maior que o stale-threshold dos confirmados:
        // nao pode ser selecionado enquanto o dispatch+ACK ainda pode estar em voo.
        long pendingGraceMs = Math.max(staleThresholdMs, properties.getPendingGraceMs());
        Instant now = Instant.now();
        Instant updatedBefore = now.minusMillis(staleThresholdMs);
        Instant pendingUpdatedBefore = now.minusMillis(pendingGraceMs);
        int maxPerRun = Math.max(0, properties.getMaxPerRun());

        RecoverStaleTransactionsResponse response = recoverStaleTransactionsPort.execute(
                new RecoverStaleTransactionsRequest(updatedBefore, pendingUpdatedBefore, maxPerRun)
        );

        if (response.scanned() > 0 || response.failed() > 0) {
            log.info("runtimeRecoveryWatchdog: scanned={} recovered={} dlq={} skipped={} failed={} cutoff={} pendingCutoff={}",
                    response.scanned(),
                    response.recovered(),
                    response.routedToDlq(),
                    response.skipped(),
                    response.failed(),
                    updatedBefore,
                    pendingUpdatedBefore);
        }

        return response;
    }
}
