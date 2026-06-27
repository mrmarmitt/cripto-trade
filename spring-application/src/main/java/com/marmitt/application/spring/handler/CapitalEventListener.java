package com.marmitt.application.spring.handler;

import com.marmitt.application.spring.deadletter.CapitalDeadLetterPayloadCodec;
import com.marmitt.core.application.reaction.ExecutionConfirmedReaction;
import com.marmitt.core.application.reaction.MarginReleasedReaction;
import com.marmitt.core.domain.portfolio.DeadLetterEntry;
import com.marmitt.core.dto.events.ExecutionConfirmedEvent;
import com.marmitt.core.dto.events.MarginReleaseEvent;
import com.marmitt.core.dto.notification.ErrorNotificationEvent;
import com.marmitt.core.enums.DlqReason;
import com.marmitt.core.ports.outbound.notification.ErrorNotificationPort;
import com.marmitt.core.ports.outbound.repository.DeadLetterEntryRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Spring listener for portfolio capital events.
 *
 * <p>Receives {@link ExecutionConfirmedEvent} and {@link MarginReleaseEvent} after
 * runner commit and delegates to portfolio reactions.</p>
 */
@Component
@Slf4j
public class CapitalEventListener {

    private final ExecutionConfirmedReaction handleExecutionConfirmed;
    private final MarginReleasedReaction handleMarginRelease;
    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final DeadLetterEntryRepositoryPort deadLetterEntryRepository;
    private final CapitalDeadLetterPayloadCodec payloadCodec;
    private final ErrorNotificationPort errorNotification;

    public CapitalEventListener(
            ExecutionConfirmedReaction handleExecutionConfirmed,
            MarginReleasedReaction handleMarginRelease,
            StrategyRunnerRepositoryPort strategyRunnerRepository,
            DeadLetterEntryRepositoryPort deadLetterEntryRepository,
            CapitalDeadLetterPayloadCodec payloadCodec,
            ErrorNotificationPort errorNotification
    ) {
        this.handleExecutionConfirmed = handleExecutionConfirmed;
        this.handleMarginRelease = handleMarginRelease;
        this.strategyRunnerRepository = strategyRunnerRepository;
        this.deadLetterEntryRepository = deadLetterEntryRepository;
        this.payloadCodec = payloadCodec;
        this.errorNotification = errorNotification;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Retryable(
            retryFor = {
                    TransientDataAccessException.class,
                    OptimisticLockingFailureException.class,
                    CannotAcquireLockException.class,
                    PessimisticLockingFailureException.class
            },
            noRetryFor = {
                    IllegalArgumentException.class,
                    IllegalStateException.class
            },
            maxAttemptsExpression = "${event.retry.max-attempts:5}",
            backoff = @Backoff(
                    delayExpression = "${event.retry.initial-interval-ms:1000}",
                    multiplierExpression = "${event.retry.multiplier:2.0}",
                    maxDelayExpression = "${event.retry.max-interval-ms:30000}"
            )
    )
    public void onExecutionConfirmed(ExecutionConfirmedEvent event) {
        withTransactionId(event.confirmation().transactionId(),
                () -> handleExecutionConfirmed.handle(event));
    }

    @Recover
    public void recoverExecutionConfirmed(Exception ex, ExecutionConfirmedEvent event) {
        withTransactionId(event.confirmation().transactionId(), () -> {
            String rawPayload = payloadCodec.encodeExecutionConfirmed(event, ex);
            persistCapitalDlq("EXECUTION_CONFIRMED", event.confirmation().runnerId(), rawPayload, ex);
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Retryable(
            retryFor = {
                    TransientDataAccessException.class,
                    OptimisticLockingFailureException.class,
                    CannotAcquireLockException.class,
                    PessimisticLockingFailureException.class
            },
            noRetryFor = {
                    IllegalArgumentException.class,
                    IllegalStateException.class
            },
            maxAttemptsExpression = "${margin.release.max-attempts:2147483647}",
            backoff = @Backoff(
                    delayExpression = "${margin.release.initial-interval-ms:1000}",
                    multiplierExpression = "${margin.release.multiplier:2.0}",
                    maxDelayExpression = "${margin.release.max-interval-ms:60000}"
            )
    )
    public void onMarginRelease(MarginReleaseEvent event) {
        withTransactionId(event.release().transactionId(),
                () -> handleMarginRelease.handle(event));
    }

    @Recover
    public void recoverMarginRelease(Exception ex, MarginReleaseEvent event) {
        withTransactionId(event.release().transactionId(), () -> {
            String rawPayload = payloadCodec.encodeMarginRelease(event, ex);
            persistCapitalDlq("MARGIN_RELEASE", event.release().runnerId(), rawPayload, ex);
        });
    }

    /**
     * Executa {@code action} com o {@code transactionId} no MDC (T26), restaurando o valor
     * anterior ao final. Garante que todos os logs do processamento do evento — incluindo
     * retries e o caminho {@code @Recover}/DLQ — carreguem o transactionId para rastreamento
     * end-to-end no Loki.
     */
    private void withTransactionId(UUID transactionId, Runnable action) {
        String previous = MDC.get("transactionId");
        if (transactionId != null) {
            MDC.put("transactionId", transactionId.toString());
        }
        try {
            action.run();
        } finally {
            if (previous != null) {
                MDC.put("transactionId", previous);
            } else {
                MDC.remove("transactionId");
            }
        }
    }

    private void persistCapitalDlq(String eventType, UUID runnerId, String rawPayload, Exception originalFailure) {
        UUID portfolioId = strategyRunnerRepository.findById(runnerId)
                .map(runner -> runner.getPortfolioId())
                .orElse(null);

        if (portfolioId == null) {
            log.error(
                    "CAPITAL_DLQ: {} exhausted retries but runner was not found - runnerId={} cause={}",
                    eventType,
                    runnerId,
                    safeMessage(originalFailure),
                    originalFailure
            );
            return;
        }

        try {
            DeadLetterEntry entry = new DeadLetterEntry(
                    portfolioId,
                    runnerId,
                    null,
                    null,
                    rawPayload,
                    DlqReason.RETRY_EXHAUSTED
            );
            deadLetterEntryRepository.save(entry);

            log.error(
                    "CAPITAL_DLQ_PERSISTED: eventType={} dlqId={} portfolioId={} runnerId={} cause={}",
                    eventType,
                    entry.getId(),
                    portfolioId,
                    runnerId,
                    safeMessage(originalFailure),
                    originalFailure
            );

            notifyDlqPersisted(eventType, runnerId, entry.getId(), originalFailure);
        } catch (Exception persistFailure) {
            log.error(
                    "CAPITAL_DLQ: failed to persist dead letter entry - eventType={} portfolioId={} runnerId={} persistCause={} originalCause={}",
                    eventType,
                    portfolioId,
                    runnerId,
                    safeMessage(persistFailure),
                    safeMessage(originalFailure),
                    persistFailure
            );
        }
    }

    private void notifyDlqPersisted(String eventType, UUID runnerId, UUID dlqId, Exception originalFailure) {
        errorNotification.notifyError(ErrorNotificationEvent.dlqPersisted(
                "Capital event sent to DLQ",
                eventType + " esgotou os retries: " + safeMessage(originalFailure),
                MDC.get("correlationId"),
                runnerId,
                dlqId,
                Instant.now()));
    }

    private static String safeMessage(Throwable throwable) {
        return throwable.getMessage() != null ? throwable.getMessage() : "<no-message>";
    }
}
