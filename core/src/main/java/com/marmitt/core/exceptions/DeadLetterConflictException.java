package com.marmitt.core.exceptions;

import java.util.UUID;

/**
 * Sinaliza que o reprocessamento de uma dead letter foi recusado (T55: entrada já resolvida é no-op,
 * não conflito): não reprocessável automaticamente, ou replay sem efeito.
 *
 * <p>Tipo explícito de domínio para o caso "conflito" (HTTP 409), substituindo a antiga
 * decisão baseada em {@code message().contains("not found")} na borda HTTP.
 */
public class DeadLetterConflictException extends RuntimeException implements CodedError {

    public DeadLetterConflictException(UUID deadLetterId, String reason) {
        super(reason);
    }

    public DeadLetterConflictException(String reason) {
        super(reason);
    }

    @Override
    public String code() {
        return "DEAD_LETTER_REPROCESS_REFUSED";
    }
}
