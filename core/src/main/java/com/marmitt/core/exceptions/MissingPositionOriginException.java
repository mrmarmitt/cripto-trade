package com.marmitt.core.exceptions;

import java.util.UUID;

/**
 * Lancada quando uma posicao ativa nao possui o vinculo da transacao BUY que a originou.
 *
 * <p>Sem esse vinculo ({@code openedByTransactionId}) o sistema nao consegue criar
 * {@code TransactionMatch} de forma confiavel no fill de SELL.
 */
public class MissingPositionOriginException extends RuntimeException {

    public MissingPositionOriginException(UUID positionId, UUID transactionId, UUID runnerId) {
        super("Position invariant violation: openedByTransactionId is required for OPEN/CLOSING position"
                + " positionId=" + positionId
                + " transactionId=" + transactionId
                + " runnerId=" + runnerId);
    }
}
