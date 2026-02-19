package com.marmitt.core.application.exception;

import com.marmitt.core.enums.RejectionReason;

import java.util.UUID;

/**
 * Lançada pelo {@code ProcessTradeSignalService} quando a reserva de capital é rejeitada.
 * <p>
 * Por ser um {@code RuntimeException}, sinaliza ao container transacional (@Transactional)
 * que o rollback deve ser executado, desfazendo a persistência da Transaction PENDING
 * e do lock de Position (quando aplicável) no mesmo banco de dados.
 * <p>
 * O caller (Handler de Spring) captura esta exceção, loga o evento e descarta o sinal —
 * não há retry para rejeição de capital.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 6.2.1 (passo 6c)</a>
 */
public class CapitalReservationRejectedException extends RuntimeException {

    private final UUID transactionId;
    private final RejectionReason reason;

    public CapitalReservationRejectedException(UUID transactionId, RejectionReason reason) {
        super("Capital reservation rejected for transaction " + transactionId + ": " + reason);
        this.transactionId = transactionId;
        this.reason = reason;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public RejectionReason getReason() {
        return reason;
    }
}
