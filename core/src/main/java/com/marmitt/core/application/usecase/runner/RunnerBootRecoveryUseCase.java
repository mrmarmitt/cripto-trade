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

import java.time.Instant;
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
    private final long pendingReconcileGraceMs;
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
                                     long pendingReconcileGraceMs,
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
        this.pendingReconcileGraceMs = Math.max(0L, pendingReconcileGraceMs);
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
        RecoveryContext recovery = new RecoveryContext(runner);

        log.info("bootRecovery: start runnerId={} exchange={} status={} reconciling={}",
                recovery.runnerId(), recovery.exchangeId(), recovery.runner().getStatus(), recovery.runner().isReconciling());

        stepAEnterReconciliation(recovery);
        step0CaptureAccountSnapshot(recovery);
        step1LoadInFlight(recovery);
        step2ResolveOrderQueryCapability(recovery);
        step4ReconcileLimbo(recovery);
        step5ValidateRemainingInFlight(recovery);
        step6FinalizeRunnerState(recovery);

        log.info("bootRecovery: completed runnerId={} inFlight={} limbo={} remaining={} hasErrors={}",
                recovery.runnerId(),
                recovery.inFlight().size(),
                recovery.limbo().size(),
                recovery.remainingInFlight(),
                recovery.hasErrors());

        return new RecoverySummary(
                recovery.runnerId(),
                recovery.inFlight().size(),
                recovery.limbo().size(),
                recovery.notes()
        );
    }

    private void stepAEnterReconciliation(RecoveryContext recovery) {
        if (recovery.runner().isReconciling()) {
            recovery.note("Step A: runner already in reconciliation.");
            return;
        }

        recovery.runner().beginReconciliation();
        strategyRunnerRepository.save(recovery.runner());
        recovery.note("Step A: runner set to reconciling=true.");
    }

    private void step0CaptureAccountSnapshot(RecoveryContext recovery) {
        Optional<ExchangeAdapterDescriptor> adapterOpt = exchangeAdapterRepository.findAdapter(recovery.exchangeId());
        if (adapterOpt.isEmpty() || !adapterOpt.get().hasAccountQuery()) {
            recovery.note("Step 0: account snapshot capability not available for exchange=" + recovery.exchangeId());
            return;
        }

        try {
            var account = adapterOpt.get().accountQuery().queryAccountSnapshot();
            recovery.note("Step 0: account snapshot captured exchange=" + recovery.exchangeId()
                    + " assets=" + account.balances().size());
        } catch (UnsupportedOperationException e) {
            recovery.note("Step 0: account snapshot unsupported for exchange=" + recovery.exchangeId());
        } catch (Exception e) {
            recovery.note("Step 0 WARN: account snapshot failed exchange=" + recovery.exchangeId()
                    + " reason=" + e.getMessage());
            log.warn("bootRecovery: account snapshot failed exchange={} runnerId={} reason={}",
                    recovery.exchangeId(), recovery.runnerId(), e.getMessage());
        }
    }

    private void step1LoadInFlight(RecoveryContext recovery) {
        List<Transaction> inFlight = strategyRunnerRepository.findByRunnerIdAndStatuses(
                recovery.runnerId(), BOOT_RELEVANT_STATUSES);
        recovery.inFlight(inFlight);

        List<Transaction> toReconcile = selectForReconciliation(
                inFlight, pendingReconcileGraceMs, Instant.now());
        recovery.limbo(toReconcile);

        int deferred = inFlight.size() - toReconcile.size();
        recovery.note("Step 1: loaded inFlight=" + inFlight.size()
                + " reconciling=" + toReconcile.size() + " deferredYoungPending=" + deferred);
    }

    /**
     * Seleciona o que sera reconciliado no Step 4. {@code SUBMITTED}/{@code PARTIAL} (confirmados)
     * sempre entram; um {@code PENDING} so entra se for mais velho que a carencia — um PENDING jovem
     * (ordem possivelmente enviada logo antes do crash, ainda nao visivel na query da exchange) e
     * DEFERIDO para nao ser expirado prematuramente; o watchdog de runtime o trata depois.
     *
     * <p>Carencia {@code <= 0} significa "sem carencia" -> todos os PENDING reconciliam
     * (cutoff = {@link Instant#MAX}, nunca "isAfter").
     */
    static List<Transaction> selectForReconciliation(List<Transaction> inFlight,
                                                     long pendingGraceMs,
                                                     Instant now) {
        Instant graceCutoff = pendingGraceMs > 0 ? now.minusMillis(pendingGraceMs) : Instant.MAX;
        return inFlight.stream()
                .filter(tx -> tx.getStatus() != TransactionStatus.PENDING
                        || tx.getRequestedAt() == null
                        || !tx.getRequestedAt().isAfter(graceCutoff))
                .toList();
    }

    private void step2ResolveOrderQueryCapability(RecoveryContext recovery) {
        if (recovery.limbo().isEmpty()) {
            recovery.note("Step 2: no limbo transactions - exchange query not required.");
            return;
        }

        Optional<ExchangeAdapterDescriptor> adapterForQuery = exchangeAdapterRepository.findAdapter(recovery.exchangeId());
        if (adapterForQuery.isEmpty() || !adapterForQuery.get().hasOrderQuery()) {
            recovery.error("Step 2 ERROR: exchange does not expose order query capability exchange=" + recovery.exchangeId());
            log.warn("bootRecovery: missing order query capability exchange={} runnerId={}",
                    recovery.exchangeId(), recovery.runnerId());
            return;
        }

        recovery.orderQuery(adapterForQuery.get().orderQuery());
        recovery.note("Step 2: order query capability resolved for exchange=" + recovery.exchangeId());
    }

    private void step4ReconcileLimbo(RecoveryContext recovery) {
        if (recovery.limbo().isEmpty()) {
            recovery.note("Step 4: no limbo transactions to reconcile.");
            return;
        }
        if (recovery.orderQuery() == null) {
            recovery.error("Step 4 ERROR: limbo exists but order query capability is unavailable.");
            // Cada transacao em limbo fica sem reconciliacao aqui — registra a falha por transacao
            // (transactionId no texto da mensagem) para diagnostico. O rastreamento estruturado por
            // MDC fica a cargo da conciliacao, nao deste orquestrador de boot.
            for (Transaction tx : recovery.limbo()) {
                log.error("bootRecovery: limbo not reconciled - order query capability unavailable"
                                + " transactionId={} runnerId={} exchange={}",
                        tx.getId(), recovery.runnerId(), recovery.exchangeId());
            }
            return;
        }

        List<Transaction> limbo = recovery.limbo();
        for (int limboIndex = 0; limboIndex < limbo.size(); limboIndex++) {
            Transaction tx = limbo.get(limboIndex);
            try {
                RecoverTransactionStatusResponse response = recoverTransactionStatusUseCase.execute(
                        RecoverTransactionStatusRequest.forBoot(tx.getId()),
                        (orderQuery, transaction, runner) -> queryOrderByClientOrderIdWithRetry(recovery, transaction)
                );

                if (response.outcome() == RecoverTransactionStatusResponse.RecoveryOutcome.RECOVERED) {
                    if (response.action() == RecoverTransactionStatusResponse.RecoveryAction.RECONCILED_FROM_EXCHANGE) {
                        recovery.note("Step 4: reconciled from exchange transactionId=" + tx.getId()
                                + " status=" + response.statusAfter());
                    } else if (response.action() == RecoverTransactionStatusResponse.RecoveryAction.MARKED_CANCELED
                            || response.action() == RecoverTransactionStatusResponse.RecoveryAction.MARKED_EXPIRED) {
                        String fallbackStatus = response.action() == RecoverTransactionStatusResponse.RecoveryAction.MARKED_CANCELED
                                ? OrderDataDto.OrderStatus.CANCELED.name()
                                : OrderDataDto.OrderStatus.EXPIRED.name();
                        recovery.note("Step 4: exchange not found -> local " + fallbackStatus
                                + " transactionId=" + tx.getId());
                    }
                    continue;
                }

                if (response.outcome() == RecoverTransactionStatusResponse.RecoveryOutcome.SKIPPED) {
                    recovery.note("Step 4: skip transactionId=" + tx.getId()
                            + " status=" + response.statusAfter()
                            + " reason=" + response.message());
                    continue;
                }

                if (response.failureReason() == RecoverTransactionStatusResponse.FailureReason.ORDER_QUERY_UNSUPPORTED) {
                    // order-query unsupported vale para a exchange/runner inteira: as transacoes de
                    // limbo restantes nao serao reconciliadas. Loga cada uma com seu transactionId
                    // (no texto) antes de interromper, para nenhuma ficar sem rastro de falha.
                    for (Transaction unreconciled : limbo.subList(limboIndex, limbo.size())) {
                        recovery.error("Step 4 ERROR: exchange query unsupported exchange=" + recovery.exchangeId()
                                + " transactionId=" + unreconciled.getId());
                        log.warn("bootRecovery: order query unsupported exchange={} runnerId={} transactionId={}",
                                recovery.exchangeId(), recovery.runnerId(), unreconciled.getId());
                    }
                    break;
                }

                recovery.error("Step 4 ERROR: transactionId=" + tx.getId() + " reason=" + response.message());
                log.error("bootRecovery: failed to reconcile limbo transactionId={} runnerId={} reason={}",
                        tx.getId(), recovery.runnerId(), response.message());
            } catch (Exception e) {
                recovery.error("Step 4 ERROR: transactionId=" + tx.getId() + " reason=" + e.getMessage());
                log.error("bootRecovery: failed to reconcile limbo transactionId={} runnerId={}",
                        tx.getId(), recovery.runnerId(), e);
            }
        }
    }

    private Optional<OrderDataDto> queryOrderByClientOrderIdWithRetry(RecoveryContext recovery, Transaction tx) {
        long backoffMs = exchangeQueryInitialBackoffMs;

        for (int attempt = 1; attempt <= exchangeQueryMaxAttempts; attempt++) {
            try {
                Optional<OrderDataDto> queried = queryOrderByClientOrderIdWithTimeout(recovery.orderQuery(), tx);
                if (attempt > 1) {
                    recovery.note("Step 4: exchange query recovered transactionId=" + tx.getId()
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
                        recovery.exchangeId(), recovery.runnerId(), tx.getId(), attempt, exchangeQueryMaxAttempts, e.getMessage());
                recovery.note("Step 4 WARN: transient query failure transactionId=" + tx.getId()
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

    private void step5ValidateRemainingInFlight(RecoveryContext recovery) {
        int remaining = strategyRunnerRepository
                .findByRunnerIdAndStatuses(recovery.runnerId(), BOOT_RELEVANT_STATUSES)
                .size();
        recovery.remainingInFlight(remaining);
        recovery.note("Step 5: remainingInFlight=" + remaining);
    }

    private void step6FinalizeRunnerState(RecoveryContext recovery) {
        StrategyRunner latestRunner = strategyRunnerRepository.findById(recovery.runnerId()).orElse(null);
        if (latestRunner == null) {
            recovery.error("Step 6 ERROR: runner not found during finalization runnerId=" + recovery.runnerId());
            return;
        }

        boolean hasRunnerScopedDlq = deadLetterEntryRepository.existsUnresolvedByRunnerId(recovery.runnerId());
        boolean hasPortfolioUnscopedDlq = deadLetterEntryRepository
                .existsUnresolvedByPortfolioIdAndRunnerIsNull(latestRunner.getPortfolioId());
        boolean dlqPending = hasRunnerScopedDlq || hasPortfolioUnscopedDlq;
        if (dlqPending) {
            recovery.error("Step 6 ERROR: unresolved DLQ entries found for runner/portfolio"
                    + " runnerId=" + recovery.runnerId()
                    + " portfolioId=" + latestRunner.getPortfolioId());
        }

        if (!recovery.hasErrors()) {
            RunnerStatus currentStatus = latestRunner.getStatus();
            if (currentStatus == RunnerStatus.CREATED) {
                latestRunner.startInitializing();
                latestRunner.activate();
                recovery.note("Step 6: runner activated CREATED->ACTIVE.");
            } else if (currentStatus == RunnerStatus.INITIALIZING) {
                latestRunner.activate();
                recovery.note("Step 6: runner activated INITIALIZING->ACTIVE.");
            } else {
                latestRunner.completeReconciliation();
                recovery.note("Step 6: reconciliation completed and runner persisted.");
            }
            strategyRunnerRepository.save(latestRunner);
            return;
        }

        if (latestRunner.getStatus() == RunnerStatus.ACTIVE) {
            latestRunner.halt();
            strategyRunnerRepository.save(latestRunner);
            recovery.note("Step 6: runner moved ACTIVE->HALTED due to reconciliation errors.");
            // Sentinel emitido apenas quando este step de fato halta o runner por DLQ pendente,
            // para o alerta #alerts-error nao disparar em runners nao-ACTIVE (CREATED/INITIALIZING)
            // ou em halts motivados por outros erros de reconciliacao.
            if (dlqPending) {
                log.warn("bootRecovery: runner halted runnerId={} reason=DLQ_PENDING portfolioId={}",
                        recovery.runnerId(), latestRunner.getPortfolioId());
            }
            return;
        }

        recovery.note("Step 6: reconciliation NOT completed due to previous errors.");
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
