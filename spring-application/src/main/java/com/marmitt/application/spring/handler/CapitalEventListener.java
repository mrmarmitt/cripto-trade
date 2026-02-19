package com.marmitt.application.spring.handler;

import com.marmitt.core.application.usecase.portfolio.HandleExecutionConfirmedService;
import com.marmitt.core.application.usecase.portfolio.HandleMarginReleaseService;
import com.marmitt.core.dto.events.ExecutionConfirmedEvent;
import com.marmitt.core.dto.events.MarginReleaseEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listener Spring para eventos de capital entre agregados (F1-11).
 * <p>
 * Processa {@link ExecutionConfirmedEvent} e {@link MarginReleaseEvent} publicados pelo Runner
 * e delega o processamento para os serviços de Portfolio.
 * <p>
 * <b>Garantias de entrega:</b>
 * <ul>
 *   <li>{@code @TransactionalEventListener(AFTER_COMMIT)}: garante que o evento só é processado
 *       após o commit do Runner no banco, evitando que o Portfolio leia dados ainda não persistidos.</li>
 *   <li>{@code confirmExecution}: at-least-once com max 5 tentativas + backoff exponencial (1s → 2s → 4s...).
 *       Após esgotar, loga como DLQ candidato (persistência em {@code dead_letter_entries} prevista para V2).</li>
 *   <li>{@code release}: retries ilimitados — capital preso (starvation) é inaceitável.</li>
 * </ul>
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 5.3.1</a>
 */
@Component
@Slf4j
public class CapitalEventListener {

    private final HandleExecutionConfirmedService handleExecutionConfirmed;
    private final HandleMarginReleaseService handleMarginRelease;

    public CapitalEventListener(
            HandleExecutionConfirmedService handleExecutionConfirmed,
            HandleMarginReleaseService handleMarginRelease
    ) {
        this.handleExecutionConfirmed = handleExecutionConfirmed;
        this.handleMarginRelease = handleMarginRelease;
    }

    /**
     * Processa confirmação de execução após commit do Runner.
     * At-least-once com 5 tentativas e backoff exponencial (1s, 2s, 4s, max 30s).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Retryable(
            maxAttempts = 5,
            backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 30000)
    )
    public void onExecutionConfirmed(ExecutionConfirmedEvent event) {
        handleExecutionConfirmed.handle(event);
    }

    /**
     * Fallback após esgotar retries de confirmação de execução.
     * Loga o evento para auditoria manual (DLQ em dead_letter_entries previsto para V2).
     */
    @Recover
    public void recoverExecutionConfirmed(Exception ex, ExecutionConfirmedEvent event) {
        log.error("CAPITAL_DLQ: confirmExecution exhausted retries — matchId={} transactionId={} — manual intervention required. Cause: {}",
                event.confirmation().matchId(),
                event.confirmation().transactionId(),
                ex.getMessage());
    }

    /**
     * Processa estorno de margem após commit do Runner.
     * Retries ilimitados — capital preso (starvation) é inaceitável.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Retryable(
            maxAttempts = Integer.MAX_VALUE,
            backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 60000)
    )
    public void onMarginRelease(MarginReleaseEvent event) {
        handleMarginRelease.handle(event);
    }
}
