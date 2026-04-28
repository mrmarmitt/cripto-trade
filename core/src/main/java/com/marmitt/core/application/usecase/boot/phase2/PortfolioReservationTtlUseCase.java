package com.marmitt.core.application.usecase.boot.phase2;

import com.marmitt.core.application.usecase.runner.orderconciliation.ConciliationOrderUpdateExecutor;
import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.portfolio.PortfolioReservationTtlResult;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Phase 2 (Portfolio): expira reservas locais "zumbis" por TTL durante o boot.
 *
 * <p>Escopo:
 * <ul>
 *   <li>Somente transacoes PENDING.</li>
 *   <li>Somente transacoes sem exchangeOrderId.</li>
 *   <li>Somente quando requestedAt <= now - ttlMs.</li>
 * </ul>
 */
@Slf4j
public class PortfolioReservationTtlUseCase {

    private static final int MAX_SAMPLES = 10;
    private static final List<TransactionStatus> PENDING_STATUS = List.of(TransactionStatus.PENDING);

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final ConciliationOrderUpdateExecutor conciliationOrderUpdate;

    public PortfolioReservationTtlUseCase(StrategyRunnerRepositoryPort strategyRunnerRepository,
                                          ConciliationOrderUpdateExecutor conciliationOrderUpdate) {
        this.strategyRunnerRepository = strategyRunnerRepository;
        this.conciliationOrderUpdate = conciliationOrderUpdate;
    }

    public PortfolioReservationTtlResult execute(UUID portfolioId,
                                                 String exchangeId,
                                                 long ttlMs,
                                                 List<StrategyRunner> scopedRunners) {
        if (ttlMs <= 0) {
            return PortfolioReservationTtlResult.skipped(
                    portfolioId, exchangeId, ttlMs, "TTL_DISABLED",
                    "Reservation TTL is disabled (ttlMs <= 0).");
        }
        if (scopedRunners == null || scopedRunners.isEmpty()) {
            return PortfolioReservationTtlResult.skipped(
                    portfolioId, exchangeId, ttlMs, "NO_RUNNERS",
                    "No eligible runners found for portfolio/exchange.");
        }

        Instant cutoff = Instant.now().minusMillis(ttlMs);
        int scannedPendingCount = 0;
        int eligibleNoExchangeOrderIdCount = 0;
        int expiredCount = 0;
        int freshCount = 0;
        int errorCount = 0;
        List<UUID> samples = new ArrayList<>();

        for (StrategyRunner runner : scopedRunners) {
            List<Transaction> pending = strategyRunnerRepository
                    .findByRunnerIdAndStatuses(runner.getId(), PENDING_STATUS);
            scannedPendingCount += pending.size();

            for (Transaction tx : pending) {
                if (hasExchangeOrderId(tx)) {
                    continue;
                }
                eligibleNoExchangeOrderIdCount++;

                if (tx.getRequestedAt() != null && tx.getRequestedAt().isAfter(cutoff)) {
                    freshCount++;
                    continue;
                }

                try {
                    conciliationOrderUpdate.execute(buildSyntheticExpired(tx));
                    expiredCount++;
                    if (samples.size() < MAX_SAMPLES) {
                        samples.add(tx.getId());
                    }
                } catch (Exception e) {
                    errorCount++;
                    log.error(
                            "reservationTtl: failed expiring transactionId={} runnerId={} portfolioId={} exchange={} reason={}",
                            tx.getId(),
                            tx.getRunnerId(),
                            portfolioId,
                            exchangeId,
                            e.getMessage(),
                            e
                    );
                }
            }
        }

        if (errorCount > 0) {
            return PortfolioReservationTtlResult.failed(
                    portfolioId,
                    exchangeId,
                    ttlMs,
                    "TTL_PARTIAL_FAILURE",
                    "One or more transactions failed to expire during boot reservation TTL cleanup.",
                    scannedPendingCount,
                    eligibleNoExchangeOrderIdCount,
                    expiredCount,
                    freshCount,
                    errorCount,
                    samples
            );
        }

        if (expiredCount > 0) {
            return PortfolioReservationTtlResult.expired(
                    portfolioId,
                    exchangeId,
                    ttlMs,
                    scannedPendingCount,
                    eligibleNoExchangeOrderIdCount,
                    expiredCount,
                    freshCount,
                    samples
            );
        }

        return PortfolioReservationTtlResult.clean(
                portfolioId,
                exchangeId,
                ttlMs,
                scannedPendingCount,
                eligibleNoExchangeOrderIdCount,
                freshCount
        );
    }

    private static boolean hasExchangeOrderId(Transaction tx) {
        String exchangeOrderId = tx.getExchangeOrderId();
        return exchangeOrderId != null && !exchangeOrderId.isBlank();
    }

    private static OrderDataDto buildSyntheticExpired(Transaction tx) {
        return new OrderDataDto(
                tx.getExchangeOrderId() != null ? tx.getExchangeOrderId() : "BOOT_TTL_" + tx.getId(),
                tx.getClientOrderId(),
                Symbol.of(tx.getSymbol()),
                tx.isBuy() ? OrderDataDto.OrderSide.BUY : OrderDataDto.OrderSide.SELL,
                OrderDataDto.OrderType.LIMIT,
                tx.getQuantity(),
                tx.getEffectiveExecutedQuantity(),
                tx.getPrice(),
                tx.getEffectiveExecutedPrice(),
                BigDecimal.ZERO,
                OrderDataDto.OrderStatus.EXPIRED,
                "BOOT_TTL_EXPIRED",
                Instant.now()
        );
    }
}
