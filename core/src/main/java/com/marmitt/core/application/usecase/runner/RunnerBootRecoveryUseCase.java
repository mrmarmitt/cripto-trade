package com.marmitt.core.application.usecase.runner;

import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.runner.RecoveryContext;
import com.marmitt.core.dto.runner.request.RecoverTransactionStatusRequest;
import com.marmitt.core.dto.runner.response.RecoverTransactionStatusResponse;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.exceptions.ExchangeQueryException;
import com.marmitt.core.ports.outbound.exchange.ExchangeAdapterDescriptor;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeAccountQueryPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderQueryPort;
import com.marmitt.core.ports.outbound.repository.DeadLetterEntryRepositoryPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Boot recovery executavel por runner.
 *
 * <p>Fluxo linear e explicito (IG 6.6 / 9.6):
 * <ul>
 *   <li>Step 0: snapshot de conta (observabilidade)</li>
 *   <li>Step 1: carregar transacoes em voo (inflight a reconciliar)</li>
 *   <li>Step 2: resolver capacidade de query na exchange</li>
 *   <li>Step 4: reconciliacao do inflight com a exchange (query-before-expire;
 *       cobre PENDING/SUBMITTED/PARTIAL pelo mesmo caminho)</li>
 *   <li>Step 5: validacao final de inflight remanescente</li>
 *   <li>Step 6: concluir reconcilicao ou halting por erro</li>
 * </ul>
 *
 * <p>O antigo Step 3 (expiracao local cega de zombie) foi removido: um PENDING sem
 * {@code exchangeOrderId} agora e consultado na exchange antes de expirar, pelo mesmo
 * {@link RecoverTransactionStatusUseCase} usado para o limbo. A limpeza de zombie em
 * runtime vive no {@link RecoverStaleTransactionsUseCase} (T31).
 *
 * <p>Dependencia entre passos:
 * Step 2 depende de Step 1 (inflight identificado).
 * Step 4 depende de Step 1 e Step 2.
 * Steps 5/6 dependem da execucao completa de Step 4.
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
    private final RecoverTransactionStatusUseCase recoverTransactionStatusUseCase;
    private final long exchangeQueryTimeoutMs;
    private final int exchangeQueryMaxAttempts;
    private final long exchangeQueryInitialBackoffMs;
    private final double exchangeQueryBackoffMultiplier;
    private final long exchangeQueryMaxBackoffMs;
    private final Executor exchangeQueryExecutor;

    public RunnerBootRecoveryUseCase(StrategyRunnerRepositoryPort strategyRunnerRepository,
                                     ExchangeAdapterRepositoryPort exchangeAdapterRepository,
                                     DeadLetterEntryRepositoryPort deadLetterEntryRepository,
                                     RecoverTransactionStatusUseCase recoverTransactionStatusUseCase,
                                     long exchangeQueryTimeoutMs,
                                     int exchangeQueryMaxAttempts,
                                     long exchangeQueryInitialBackoffMs,
                                     double exchangeQueryBackoffMultiplier,
                                     long exchangeQueryMaxBackoffMs,
                                     Executor exchangeQueryExecutor) {
        this.strategyRunnerRepository = strategyRunnerRepository;
        this.exchangeAdapterRepository = exchangeAdapterRepository;
        this.deadLetterEntryRepository = deadLetterEntryRepository;
        this.recoverTransactionStatusUseCase = recoverTransactionStatusUseCase;
        this.exchangeQueryTimeoutMs = Math.max(0L, exchangeQueryTimeoutMs);
        this.exchangeQueryMaxAttempts = Math.max(1, exchangeQueryMaxAttempts);
        this.exchangeQueryInitialBackoffMs = Math.max(0L, exchangeQueryInitialBackoffMs);
        this.exchangeQueryBackoffMultiplier = exchangeQueryBackoffMultiplier > 0
                ? exchangeQueryBackoffMultiplier
                : 1.0d;
        this.exchangeQueryMaxBackoffMs = Math.max(0L, exchangeQueryMaxBackoffMs);
        this.exchangeQueryExecutor = exchangeQueryExecutor;
    }

    public RecoverySummary recoverRunner(StrategyRunner runner) {
        RecoveryContext ctx = new RecoveryContext(runner);

        log.info("bootRecovery: start runnerId={} exchange={} status={} reconciling={}",
                ctx.runnerId(), ctx.exchangeId(), ctx.runner().getStatus(), ctx.runner().isReconciling());

        stepAEnterReconciliation(ctx);
        step0CaptureAccountSnapshot(ctx);
        step1LoadInFlight(ctx);
        step2ResolveOrderQueryCapability(ctx);
        step4ReconcileLimbo(ctx);
        step5ValidateRemainingInFlight(ctx);
        step6FinalizeRunnerState(ctx);

        log.info("bootRecovery: completed runnerId={} inFlight={} limbo={} remaining={} hasErrors={}",
                ctx.runnerId(),
                ctx.inFlight().size(),
                ctx.limbo().size(),
                ctx.remainingInFlight(),
                ctx.hasErrors());

        return new RecoverySummary(
                ctx.runnerId(),
                ctx.inFlight().size(),
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
        Optional<ExchangeAdapterDescriptor> adapterOpt = exchangeAdapterRepository.findAdapter(ctx.exchangeId());
        if (adapterOpt.isEmpty() || !adapterOpt.get().hasAccountQuery()) {
            ctx.note("Step 0: account snapshot capability not available for exchange=" + ctx.exchangeId());
            return;
        }

        try {
            var account = adapterOpt.get().accountQuery().queryAccountSnapshot();
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

    private void step1LoadInFlight(RecoveryContext ctx) {
        List<Transaction> inFlight = strategyRunnerRepository.findByRunnerIdAndStatuses(
                ctx.runnerId(), BOOT_RELEVANT_STATUSES);
        ctx.inFlight(inFlight);
        // PENDING (sem exchangeOrderId), SUBMITTED e PARTIAL seguem o mesmo caminho de
        // reconciliacao com query-before-expire. Nao ha mais classificacao/expiracao de zombie.
        ctx.limbo(inFlight);

        ctx.note("Step 1: loaded inFlight=" + inFlight.size() + " (all routed to reconciliation)");
    }

    private void step2ResolveOrderQueryCapability(RecoveryContext ctx) {
        if (ctx.limbo().isEmpty()) {
            ctx.note("Step 2: no limbo transactions - exchange query not required.");
            return;
        }

        Optional<ExchangeAdapterDescriptor> adapterForQuery = exchangeAdapterRepository.findAdapter(ctx.exchangeId());
        if (adapterForQuery.isEmpty() || !adapterForQuery.get().hasOrderQuery()) {
            ctx.error("Step 2 ERROR: exchange does not expose order query capability exchange=" + ctx.exchangeId());
            log.warn("bootRecovery: missing order query capability exchange={} runnerId={}",
                    ctx.exchangeId(), ctx.runnerId());
            return;
        }

        ctx.orderQuery(adapterForQuery.get().orderQuery());
        ctx.note("Step 2: order query capability resolved for exchange=" + ctx.exchangeId());
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
                RecoverTransactionStatusResponse response = recoverTransactionStatusUseCase.execute(
                        RecoverTransactionStatusRequest.forBoot(tx.getId()),
                        (orderQuery, transaction, runner) -> queryOrderByClientOrderIdWithRetry(ctx, transaction)
                );

                if (response.outcome() == RecoverTransactionStatusResponse.RecoveryOutcome.RECOVERED) {
                    if (response.action() == RecoverTransactionStatusResponse.RecoveryAction.RECONCILED_FROM_EXCHANGE) {
                        ctx.note("Step 4: reconciled from exchange transactionId=" + tx.getId()
                                + " status=" + response.statusAfter());
                    } else if (response.action() == RecoverTransactionStatusResponse.RecoveryAction.MARKED_CANCELED
                            || response.action() == RecoverTransactionStatusResponse.RecoveryAction.MARKED_EXPIRED) {
                        String fallbackStatus = response.action() == RecoverTransactionStatusResponse.RecoveryAction.MARKED_CANCELED
                                ? OrderDataDto.OrderStatus.CANCELED.name()
                                : OrderDataDto.OrderStatus.EXPIRED.name();
                        ctx.note("Step 4: exchange not found -> local " + fallbackStatus
                                + " transactionId=" + tx.getId());
                    }
                    continue;
                }

                if (response.outcome() == RecoverTransactionStatusResponse.RecoveryOutcome.SKIPPED) {
                    ctx.note("Step 4: skip transactionId=" + tx.getId()
                            + " status=" + response.statusAfter()
                            + " reason=" + response.message());
                    continue;
                }

                if (response.failureReason() == RecoverTransactionStatusResponse.FailureReason.ORDER_QUERY_UNSUPPORTED) {
                    ctx.error("Step 4 ERROR: exchange query unsupported exchange=" + ctx.exchangeId()
                            + " transactionId=" + tx.getId());
                    log.warn("bootRecovery: order query unsupported exchange={} runnerId={} transactionId={}",
                            ctx.exchangeId(), ctx.runnerId(), tx.getId());
                    break;
                }

                ctx.error("Step 4 ERROR: transactionId=" + tx.getId() + " reason=" + response.message());
                log.error("bootRecovery: failed to reconcile limbo transactionId={} runnerId={} reason={}",
                        tx.getId(), ctx.runnerId(), response.message());
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
                () -> orderQuery.queryOrderByClientOrderId(tx.getSymbol(), tx.getClientOrderId()),
                exchangeQueryExecutor
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
        if (throwable instanceof ExchangeQueryException exchangeQueryException) {
            return exchangeQueryException.isRetryable();
        }
        if (throwable instanceof BootQueryTimeoutException) {
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
        StrategyRunner latestRunner = strategyRunnerRepository.findById(ctx.runnerId()).orElse(null);
        if (latestRunner == null) {
            ctx.error("Step 6 ERROR: runner not found during finalization runnerId=" + ctx.runnerId());
            return;
        }

        boolean hasRunnerScopedDlq = deadLetterEntryRepository.existsUnresolvedByRunnerId(ctx.runnerId());
        boolean hasPortfolioUnscopedDlq = deadLetterEntryRepository
                .existsUnresolvedByPortfolioIdAndRunnerIsNull(latestRunner.getPortfolioId());
        if (hasRunnerScopedDlq || hasPortfolioUnscopedDlq) {
            ctx.error("Step 6 ERROR: unresolved DLQ entries found for runner/portfolio"
                    + " runnerId=" + ctx.runnerId()
                    + " portfolioId=" + latestRunner.getPortfolioId());
        }

        if (!ctx.hasErrors()) {
            RunnerStatus currentStatus = latestRunner.getStatus();
            if (currentStatus == RunnerStatus.CREATED) {
                latestRunner.startInitializing();
                latestRunner.activate();
                ctx.note("Step 6: runner activated CREATED->ACTIVE.");
            } else if (currentStatus == RunnerStatus.INITIALIZING) {
                latestRunner.activate();
                ctx.note("Step 6: runner activated INITIALIZING->ACTIVE.");
            } else {
                latestRunner.completeReconciliation();
                ctx.note("Step 6: reconciliation completed and runner persisted.");
            }
            strategyRunnerRepository.save(latestRunner);
            return;
        }

        if (latestRunner.getStatus() == RunnerStatus.ACTIVE) {
            latestRunner.halt();
            strategyRunnerRepository.save(latestRunner);
            ctx.note("Step 6: runner moved ACTIVE->HALTED due to reconciliation errors.");
            return;
        }

        ctx.note("Step 6: reconciliation NOT completed due to previous errors.");
    }

    public record RecoverySummary(
            UUID runnerId,
            int inFlightCount,
            int limboCount,
            List<String> notes
    ) {}

    private static final class BootQueryTimeoutException extends RuntimeException {
        private BootQueryTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
