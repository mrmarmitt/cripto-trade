package com.marmitt.core.exceptions;

import java.util.UUID;

/**
 * Sinaliza conflito de estado ao operar uma dead letter (ex.: já resolvida, não
 * reprocessável automaticamente, ou replay sem efeito).
 *
 * <p>Tipo explícito de domínio para o caso "conflito" (HTTP 409), substituindo a antiga
 * decisão baseada em {@code message().contains("not found")} na borda HTTP.
 */
public class DeadLetterConflictException extends RuntimeException {

    public DeadLetterConflictException(UUID deadLetterId, String reason) {
        super(reason);
    }

    public DeadLetterConflictException(String reason) {
        super(reason);
    }
}
