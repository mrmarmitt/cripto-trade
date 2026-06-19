package com.marmitt.core.exceptions;

import java.util.UUID;

/**
 * Sinaliza que a dead letter solicitada não existe.
 *
 * <p>Tipo explícito de domínio para o caso "não encontrado", substituindo a antiga
 * decisão baseada em {@code message().contains("not found")} na borda HTTP.
 */
public class DeadLetterNotFoundException extends RuntimeException {

    public DeadLetterNotFoundException(UUID deadLetterId) {
        super("Dead letter entry not found: " + deadLetterId);
    }
}
