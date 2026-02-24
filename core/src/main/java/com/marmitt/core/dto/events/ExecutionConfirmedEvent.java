package com.marmitt.core.dto.events;

import com.marmitt.core.dto.capital.ExecutionConfirmation;

import java.util.Objects;

/**
 * Evento publicado pelo Runner após cada {@code TransactionMatch} ser persistido.
 * Dispara o processamento assíncrono de confirmação no Portfolio (Reserved → Realized).
 * <p>
 * Publicado via {@code EventPublisherPort} e processado por {@code ExecutionConfirmedReaction}
 * com garantia at-least-once ({@code @TransactionalEventListener(phase = AFTER_COMMIT)}).
 * A idempotência é garantida pelo {@code matchId} — duplicatas são descartadas silenciosamente.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 5.2.2, 5.3.1</a>
 */
public record ExecutionConfirmedEvent(ExecutionConfirmation confirmation) {

    public ExecutionConfirmedEvent {
        Objects.requireNonNull(confirmation, "confirmation cannot be null");
    }
}
