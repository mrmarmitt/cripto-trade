package com.marmitt.core.application.usecase.runner;

import com.marmitt.core.application.usecase.runner.orderconciliation.ConciliationOrderUpdateExecutor;
import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.runner.RecoveryContext;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeAccountQueryPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderQueryPort;
import com.marmitt.core.ports.outbound.repository.DeadLetterEntryRepositoryPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Boot recovery executavel por runner.
 *
 * <p>Fluxo linear e explicito (IG 6.6 / 9.6):
 * <ul>
 *   <li>Step 0: snapshot de conta (observabilidade)</li>
 *   <li>Step 1: carregar transacoes em voo e classificar zombies/limbo</li>
 *   <li>Step 2: resolver capacidade de query na exchange (quando houver limbo)</li>
 *   <li>Step 3: saneamento de zombies</li>
 *   <li>Step 4: reconciliacao do limbo com a exchange</li>
 *   <li>Step 5: validacao final de inflight remanescente</li>
 *   <li>Step 6: concluir reconcilicao ou halting por erro</li>
 * </ul>
 *
 * <p>Dependencia entre passos:
 * Step 2 depende de Step 1 (limbo identificado).
 * Steps 3/4 dependem de Step 1 e Step 2.
 * Steps 5/6 dependem da execucao completa de 3/4.
 */
@Slf4j
public class RunnerBootRecoveryUseCase {

    private static final List<TransactionStatus> BOOT_RELEVANT_STATUSES = List.of(
            TransactionStatus.PENDING,
            TransactionStatus.SUBMITTED,
            TransactionStatus.PARTIAL
    );

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final ExchangeAdapterRepositoryPort exchangeAdapterRepository;
    private final DeadLetterEntryRepositoryPort deadLetterEntryRepository;
    private final ConciliationOrderUpdateExecutor conciliationOrderUpdate;
    private final long pendingWithoutExchangeOrderIdTtlMs;
    private final long exchangeQueryTimeoutMs;
    private final int exchangeQueryMaxAttempts;
    private final long exchangeQueryInitialBackoffMs;
    private final double exchangeQueryBackoffMultiplier;
    private final long exchangeQueryMaxBackoffMs;

    public RunnerBootRecoveryUseCase(StrategyRunnerRepositoryPort strategyRunnerRepository,
                                     ExchangeAdapterRepositoryPort exchangeAdapterRepository,
                                     DeadLetterEntryRepositoryPort deadLetterEntryRepository,
                                     ConciliationOrderUpdateExecutor conciliationOrderUpdate,
                                     long pendingWithoutExchangeOrderIdTtlMs,
                                     long exchangeQueryTimeoutMs,
                                     int exchangeQueryMaxAttempts,
                                     long exchangeQueryInitialBackoffMs,
                                     double exchangeQueryBackoffMultiplier,
                                     long exchangeQueryMaxBackoffMs) {
        this.strategyRunnerRepository = strategyRunnerRepository;
        this.exchangeAdapterRepository = exchangeAdapterRepository;
        this.deadLetterEntryRepository = deadLetterEntryRepository;
        this.conciliationOrderUpdate = conciliationOrderUpdate;
        this.pendingWithoutExchangeOrderIdTtlMs = pendingWithoutExchangeOrderIdTtlMs;
        this.exchangeQueryTimeoutMs = Math.max(0L, exchangeQueryTimeoutMs);
        this.exchangeQueryMaxAttempts = Math.max(1, exchangeQueryMaxAttempts);
        this.exchangeQueryInitialBackoffMs = Math.max(0L, exchangeQueryInitialBackoffMs);
        this.exchangeQueryBackoffMultiplier = exchangeQueryBackoffMultiplier > 0
                ? exchangeQueryBackoffMultiplier
                : 1.0d;
        this.exchangeQueryMaxBackoffMs = Math.max(0L, exchangeQueryMaxBackoffMs);
    }

    public RecoverySummary recoverRunner(StrategyRunner runner) {
        RecoveryContext ctx = new RecoveryContext(runner);

        log.info("bootRecovery: start runnerId={} exchange={} status={} reconciling={}",
                ctx.runnerId(), ctx.exchangeId(), ctx.runner().getStatus(), ctx.runner().isReconciling());

        stepAEnterReconciliation(ctx);
        step0CaptureAccountSnapshot(ctx);
        step1LoadAndClassifyInFlight(ctx);
        step2ResolveOrderQueryCapability(ctx);
        step3ExpireZombies(ctx);
        step4ReconcileLimbo(ctx);
        step5ValidateRemainingInFlight(ctx);
        step6FinalizeRunnerState(ctx);

        log.info("bootRecovery: completed runnerId={} inFlight={} zombies={} limbo={} remaining={} hasErrors={}",
                ctx.runnerId(),
                ctx.inFlight().size(),
                ctx.zombies().size(),
                ctx.limbo().size(),
                ctx.remainingInFlight(),
                ctx.hasErrors());

        return new RecoverySummary(
                ctx.runnerId(),
                ctx.inFlight().size(),
                ctx.zombies().size(),
                ctx.limbo().size(),
                ctx.notes()
        );
    }

    private void stepAEnterReconciliation(RecoveryContext ctx) {
        if (ctx.runner().isReconciling()) {
            ctx.note("Step A: runner already in reconciliation.");
            return;
        }

        ctx.runner().beginReconciliation();
        strategyRunnerRepository.save(ctx.runner());
        ctx.note("Step A: runner set to reconciling=true.");
    }

    private void step0CaptureAccountSnapshot(RecoveryContext ctx) {
        Optional<ExchangeAccountQueryPort> accountQueryOptional =
                exchangeAdapterRepository.findAccountQueryByName(ctx.exchangeId());
        if (accountQueryOptional.isEmpty()) {
            ctx.note("Step 0: account snapshot capability not available for exchange=" + ctx.exchangeId());
            return;
        }

        try {
            var account = accountQueryOptional.get().queryAccountSnapshot();
            ctx.note("Step 0: account snapshot captured exchange=" + ctx.exchangeId()
                    + " assets=" + account.balances().size());
        } catch (UnsupportedOperationException e) {
            ctx.note("Step 0: account snapshot unsupported for exchange=" + ctx.exchangeId());
        } catch (Exception e) {
            ctx.note("Step 0 WARN: account snapshot failed exchange=" + ctx.exchangeId()
                    + " reason=" + e.getMessage());
            log.warn("bootRecovery: account snapshot failed exchange={} runnerId={} reason={}",
                    ctx.exchangeId(), ctx.runnerId(), e.getMessage());
        }
    }

    private void step1LoadAndClassifyInFlight(RecoveryContext ctx) {
        List<Transaction> inFlight = strategyRunnerRepository.findByRunnerIdAndStatuses(
                ctx.runnerId(), BOOT_RELEVANT_STATUSES);
        ctx.inFlight(inFlight);

        List<Transaction> zombies = inFlight.stream()
                .filter(tx -> tx.getStatus() == TransactionStatus.PENDING)
                .filter(tx -> tx.getExchangeOrderId() == null || tx.getExchangeOrderId().isBlank())
                .toList();
        ctx.zombies(zombies);

        List<Transaction> limbo = inFlight.stream()
                .filter(tx -> !zombies.contains(tx))
                .toList();
        ctx.limbo(limbo);

        ctx.note("Step 1: classified inFlight=" + inFlight.size()
                + " zombies=" + zombies.size() + " limbo=" + limbo.size());
    }

    private void step2ResolveOrderQueryCapability(RecoveryContext ctx) {
        if (ctx.limbo().isEmpty()) {
            ctx.note("Step 2: no limbo transactions - exchange query not required.");
            return;
        }

        Optional<ExchangeOrderQueryPort> orderQueryOptional =
                exchangeAdapterRepository.findOrderQueryByName(ctx.exchangeId());
        if (orderQueryOptional.isEmpty()) {
            ctx.error("Step 2 ERROR: exchange does not expose order query capability exchange=" + ctx.exchangeId());
            log.warn("bootRecovery: missing order query capability exchange={} runnerId={}",
                    ctx.exchangeId(), ctx.runnerId());
            return;
        }

        ctx.orderQuery(orderQueryOptional.get());
        ctx.note("Step 2: order query capability resolved for exchange=" + ctx.exchangeId());
    }

    private void step3ExpireZombies(RecoveryContext ctx) {
        Instant cutoff = pendingWithoutExchangeOrderIdTtlMs > 0
                ? Instant.now().minusMillis(pendingWithoutExchangeOrderIdTtlMs)
                : Instant.EPOCH;

        for (Transaction tx : ctx.zombies()) {
            if (pendingWithoutExchangeOrderIdTtlMs > 0
                    && tx.getRequestedAt() != null
                    && tx.getRequestedAt().isAfter(cutoff)) {
                ctx.note("Step 3: zombie within TTL - keeping pending transactionId=" + tx.getId());
                continue;
            }

            try {
                OrderDataDto syntheticExpired = buildSyntheticTerminalOrder(
                        tx, OrderDataDto.OrderStatus.EXPIRED, "BOOT_ZOMBIE_PENDING_WITHOUT_EXCHANGE_ORDER_ID");
                conciliationOrderUpdate.execute(syntheticExpired);
                ctx.note("Step 3: zombie expired transactionId=" + tx.getId());
            } catch (Exception e) {
                ctx.error("Step 3 ERROR: zombie transactionId=" + tx.getId() + " reason=" + e.getMessage());
                log.error("bootRecovery: failed to expire zombie transactionId={} runnerId={}",
                        tx.getId(), ctx.runnerId(), e);
            }
        }
    }

    private void step4ReconcileLimbo(RecoveryContext ctx) {
        if (ctx.limbo().isEmpty()) {
            ctx.note("Step 4: no limbo transactions to reconcile.");
            return;
        }
        if (ctx.orderQuery() == null) {
            ctx.error("Step 4 ERROR: limbo exists but order query capability is unavailable.");
            return;
        }

        for (Transaction tx : ctx.limbo()) {
            try {
                Optional<OrderDataDto> queried = queryOrderByClientOrderIdWithRetry(ctx, tx);

                if (queried.isPresent()) {
                    OrderDataDto normalized = normalizeQueriedOrder(tx, queried.get());
                    conciliationOrderUpdate.execute(normalized);
                    ctx.note("Step 4: reconciled from exchange transactionId=" + tx.getId()
                            + " status=" + normalized.status());
                    continue;
                }

                OrderDataDto.OrderStatus fallbackStatus = tx.getStatus() == TransactionStatus.PARTIAL
                        ? OrderDataDto.OrderStatus.CANCELED
                        : OrderDataDto.OrderStatus.EXPIRED;
                OrderDataDto synthetic = buildSyntheticTerminalOrder(
                        tx, fallbackStatus, "BOOT_NOT_FOUND_ON_EXCHANGE");
                conciliationOrderUpdate.execute(synthetic);
                ctx.note("Step 4: exchange not found -> local " + fallbackStatus
                        + " transactionId=" + tx.getId());

            } catch (UnsupportedOperationException e) {
                ctx.error("Step 4 ERROR: exchange query unsupported exchange=" + ctx.exchangeId()
                        + " transactionId=" + tx.getId());
                log.warn("bootRecovery: order query unsupported exchange={} runnerId={} transactionId={}",
                        ctx.exchangeId(), ctx.runnerId(), tx.getId());
                break;
            } catch (Exception e) {
                ctx.error("Step 4 ERROR: transactionId=" + tx.getId() + " reason=" + e.getMessage());
                log.error("bootRecovery: failed to reconcile limbo transactionId={} runnerId={}",
                        tx.getId(), ctx.runnerId(), e);
            }
        }
    }

    private Optional<OrderDataDto> queryOrderByClientOrderIdWithRetry(RecoveryContext ctx, Transaction tx) {
        long backoffMs = exchangeQueryInitialBackoffMs;

        for (int attempt = 1; attempt <= exchangeQueryMaxAttempts; attempt++) {
            try {
                Optional<OrderDataDto> queried = queryOrderByClientOrderIdWithTimeout(ctx.orderQuery(), tx);
                if (attempt > 1) {
                    ctx.note("Step 4: exchange query recovered transactionId=" + tx.getId()
                            + " attempt=" + attempt);
                }
                return queried;
            } catch (UnsupportedOperationException e) {
                throw e;
            } catch (Exception e) {
                boolean retryable = isRetryableQueryFailure(e);
                boolean hasNextAttempt = attempt < exchangeQueryMaxAttempts;

                if (!retryable || !hasNextAttempt) {
                    throw e;
                }

                log.warn("bootRecovery: transient query failure exchange={} runnerId={} transactionId={} attempt={}/{} reason={}",
                        ctx.exchangeId(), ctx.runnerId(), tx.getId(), attempt, exchangeQueryMaxAttempts, e.getMessage());
                ctx.note("Step 4 WARN: transient query failure transactionId=" + tx.getId()
                        + " attempt=" + attempt + " reason=" + e.getMessage());

                sleepBackoff(backoffMs);
                backoffMs = nextBackoff(backoffMs);
            }
        }

        return Optional.empty();
    }

    private Optional<OrderDataDto> queryOrderByClientOrderIdWithTimeout(ExchangeOrderQueryPort orderQuery,
                                                                         Transaction tx) {
        if (exchangeQueryTimeoutMs <= 0) {
            return orderQuery.queryOrderByClientOrderId(tx.getSymbol(), tx.getClientOrderId());
        }

        CompletableFuture<Optional<OrderDataDto>> future = CompletableFuture.supplyAsync(
                () -> orderQuery.queryOrderByClientOrderId(tx.getSymbol(), tx.getClientOrderId())
        );

        try {
            return future.get(exchangeQueryTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new BootQueryTimeoutException(
                    "Timeout querying order by clientOrderId after " + exchangeQueryTimeoutMs
                            + "ms transactionId=" + tx.getId(), e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while querying order by clientOrderId", e);
        }
    }

    private boolean isRetryableQueryFailure(Throwable throwable) {
        if (throwable instanceof UnsupportedOperationException) {
            return false;
        }
        if (throwable instanceof IllegalArgumentException) {
            return false;
        }
        if (throwable instanceof BootQueryTimeoutException) {
            return true;
        }

        String message = throwable.getMessage() != null
                ? throwable.getMessage().toLowerCase(Locale.ROOT)
                : "";

        if (message.contains("timeout")
                || message.contains("timed out")
                || message.contains("connection reset")
                || message.contains("temporarily")
                || message.contains("rate limit")
                || message.contains("429")
                || message.contains("503")) {
            return true;
        }

        Throwable cause = throwable.getCause();
        if (cause == null || cause == throwable) {
            return false;
        }
        return isRetryableQueryFailure(cause);
    }

    private void sleepBackoff(long backoffMs) {
        if (backoffMs <= 0) {
            return;
        }
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during boot recovery backoff", e);
        }
    }

    private long nextBackoff(long currentBackoffMs) {
        if (currentBackoffMs <= 0) {
            return 0L;
        }

        long multiplied = Math.round(currentBackoffMs * exchangeQueryBackoffMultiplier);
        long bounded = Math.max(currentBackoffMs, multiplied);
        return exchangeQueryMaxBackoffMs > 0
                ? Math.min(bounded, exchangeQueryMaxBackoffMs)
                : bounded;
    }

    private void step5ValidateRemainingInFlight(RecoveryContext ctx) {
        int remaining = strategyRunnerRepository
                .findByRunnerIdAndStatuses(ctx.runnerId(), BOOT_RELEVANT_STATUSES)
                .size();
        ctx.remainingInFlight(remaining);
        ctx.note("Step 5: remainingInFlight=" + remaining);
    }

    private void step6FinalizeRunnerState(RecoveryContext ctx) {
        boolean hasRunnerScopedDlq = deadLetterEntryRepository.existsUnresolvedByRunnerId(ctx.runnerId());
        boolean hasPortfolioUnscopedDlq = deadLetterEntryRepository
                .existsUnresolvedByPortfolioIdAndRunnerIsNull(ctx.runner().getPortfolioId());
        if (hasRunnerScopedDlq || hasPortfolioUnscopedDlq) {
            ctx.error("Step 6 ERROR: unresolved DLQ entries found for runner/portfolio"
                    + " runnerId=" + ctx.runnerId()
                    + " portfolioId=" + ctx.runner().getPortfolioId());
        }

        if (!ctx.hasErrors()) {
            ctx.runner().completeReconciliation();
            strategyRunnerRepository.save(ctx.runner());
            ctx.note("Step 6: reconciliation completed and runner persisted.");
            return;
        }

        if (ctx.runner().getStatus() == RunnerStatus.ACTIVE) {
            ctx.runner().halt();
            strategyRunnerRepository.save(ctx.runner());
            ctx.note("Step 6: runner moved ACTIVE->HALTED due to reconciliation errors.");
            return;
        }

        ctx.note("Step 6: reconciliation NOT completed due to previous errors.");
    }

    private OrderDataDto normalizeQueriedOrder(Transaction tx, OrderDataDto queried) {
        Symbol symbol = queried.symbol() != null ? queried.symbol() : Symbol.of(tx.getSymbol());
        OrderDataDto.OrderSide side = queried.side() != null
                ? queried.side()
                : (tx.isBuy() ? OrderDataDto.OrderSide.BUY : OrderDataDto.OrderSide.SELL);
        OrderDataDto.OrderType type = queried.type() != null
                ? queried.type()
                : OrderDataDto.OrderType.LIMIT;

        BigDecimal quantity = queried.quantity() != null ? queried.quantity() : tx.getQuantity();
        BigDecimal executedQty = queried.executedQuantity() != null
                ? queried.executedQuantity()
                : tx.getEffectiveExecutedQuantity();
        BigDecimal price = queried.price() != null ? queried.price() : tx.getPrice();
        BigDecimal executedPrice = queried.executedPrice() != null
                ? queried.executedPrice()
                : tx.getEffectiveExecutedPrice();
        BigDecimal fee = queried.fee() != null ? queried.fee() : BigDecimal.ZERO;

        return new OrderDataDto(
                queried.orderId() != null ? queried.orderId() : tx.getExchangeOrderId(),
                queried.clientOrderId() != null ? queried.clientOrderId() : tx.getClientOrderId(),
                symbol,
                side,
                type,
                quantity,
                executedQty,
                price,
                executedPrice,
                fee,
                queried.status(),
                queried.rejectReason(),
                queried.timestamp() != null ? queried.timestamp() : Instant.now()
        );
    }

    private OrderDataDto buildSyntheticTerminalOrder(Transaction tx,
                                                     OrderDataDto.OrderStatus status,
                                                     String reason) {
        return new OrderDataDto(
                tx.getExchangeOrderId() != null ? tx.getExchangeOrderId() : "BOOT_" + tx.getId(),
                tx.getClientOrderId(),
                Symbol.of(tx.getSymbol()),
                tx.isBuy() ? OrderDataDto.OrderSide.BUY : OrderDataDto.OrderSide.SELL,
                OrderDataDto.OrderType.LIMIT,
                tx.getQuantity(),
                tx.getEffectiveExecutedQuantity(),
                tx.getPrice(),
                tx.getEffectiveExecutedPrice(),
                BigDecimal.ZERO,
                status,
                reason,
                Instant.now()
        );
    }

    public record RecoverySummary(
            UUID runnerId,
            int inFlightCount,
            int zombiesCount,
            int limboCount,
            List<String> notes
    ) {}

    private static final class BootQueryTimeoutException extends RuntimeException {
        private BootQueryTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
