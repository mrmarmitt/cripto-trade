package com.marmitt.core.application.usecase.runner;

import com.marmitt.core.domain.runner.ClientOrderId;
import com.marmitt.core.domain.runner.RunnerStatusCause;
import com.marmitt.core.dto.notification.ErrorNotificationEvent;
import com.marmitt.core.dto.runner.response.RunnerTransitionResult;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.ports.inbound.runner.HaltRunnerPort;
import com.marmitt.core.ports.outbound.notification.ErrorNotificationPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rejeições consecutivas por runner (T25, IG §11.6): {@code maxConsecutive} ordens distintas
 * rejeitadas seguidas param só aquele runner (kill switch do runner, T8). A retomada é manual pelo
 * {@code resume} (T49), sem cooldown automático — decisão de negócio.
 *
 * <p>Ordem aceita pela exchange (NEW, fill parcial ou total) zera a sequência; CANCELED/EXPIRED não
 * mexem nela. A sequência é por runner (shortCode do {@code clientOrderId}) e conta cada ordem uma vez,
 * mesmo com o REJECTED repetido pelo stream. Estado em memória: um restart recomeça a contagem.
 */
@Slf4j
public class ConsecutiveRejectionGuard {

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final HaltRunnerPort haltRunner;
    private final ErrorNotificationPort notification;
    private final Clock clock;
    private final int maxConsecutive;
    private final Map<String, Set<String>> rejectedByRunner = new ConcurrentHashMap<>();

    public ConsecutiveRejectionGuard(StrategyRunnerRepositoryPort strategyRunnerRepository,
                                     HaltRunnerPort haltRunner,
                                     ErrorNotificationPort notification,
                                     Clock clock,
                                     int maxConsecutive) {
        if (maxConsecutive <= 0) {
            throw new IllegalArgumentException("maxConsecutive must be positive: " + maxConsecutive);
        }
        this.strategyRunnerRepository = strategyRunnerRepository;
        this.haltRunner = haltRunner;
        this.notification = notification;
        this.clock = clock;
        this.maxConsecutive = maxConsecutive;
    }

    /** Registra o desfecho de uma atualização de ordem já conciliada. */
    public void onOrderUpdate(OrderDataDto orderData) {
        String shortCode = ClientOrderId.getRunnerShortCode(orderData.clientOrderId());
        if (shortCode == null || orderData.status() == null) {
            return;
        }
        switch (orderData.status()) {
            case NEW, PARTIALLY_FILLED, FILLED -> rejectedByRunner.remove(shortCode);
            case REJECTED -> onRejected(shortCode, orderData);
            default -> { }
        }
    }

    private void onRejected(String shortCode, OrderDataDto orderData) {
        Set<String> streak = rejectedByRunner.computeIfAbsent(shortCode, key -> ConcurrentHashMap.newKeySet());
        streak.add(orderData.clientOrderId());
        if (streak.size() < maxConsecutive) {
            return;
        }
        int rejected = streak.size();
        rejectedByRunner.remove(shortCode);
        strategyRunnerRepository.findTransactionByClientOrderId(orderData.clientOrderId())
                .ifPresent(transaction -> halt(transaction.getRunnerId(), rejected, orderData.rejectReason()));
    }

    private void halt(UUID runnerId, int rejected, String lastReason) {
        String details = "consecutiveRejections=" + rejected + " lastReason=" + lastReason;
        RunnerTransitionResult result = haltRunner.haltById(runnerId,
                RunnerStatusCause.automatic("consecutive order rejections: " + details));
        if (result.newStatus() != RunnerStatus.HALTED || result.previousStatus() == RunnerStatus.HALTED) {
            return;
        }
        log.error("rejectionGuard: runner halted runnerId={} {}", runnerId, details);
        notification.notifyError(ErrorNotificationEvent.runnerAutoHalted(
                "Runner halted after " + rejected + " consecutive rejections", details, runnerId, clock.instant()));
    }
}
