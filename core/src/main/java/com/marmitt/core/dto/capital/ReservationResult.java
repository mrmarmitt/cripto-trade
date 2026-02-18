package com.marmitt.core.dto.capital;

import com.marmitt.core.enums.RejectionReason;
import com.marmitt.core.enums.ReservationStatus;

import java.util.UUID;

/**
 * Resultado síncrono da operação {@code CapitalManager.reserve()}.
 * <p>
 * Se {@code status == APPROVED}: {@code reservationId} identifica o lock contábil criado;
 * {@code rejectionReason} é {@code null}.<br>
 * Se {@code status == REJECTED}: {@code reservationId} é {@code null};
 * {@code rejectionReason} indica o motivo.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 5.2.1</a>
 */
public record ReservationResult(
        ReservationStatus status,
        UUID reservationId,
        RejectionReason rejectionReason
) {
    /**
     * Cria um resultado de aprovação com o ID da reserva criada.
     */
    public static ReservationResult approved(UUID reservationId) {
        return new ReservationResult(ReservationStatus.APPROVED, reservationId, null);
    }

    /**
     * Cria um resultado de rejeição com o motivo.
     */
    public static ReservationResult rejected(RejectionReason reason) {
        return new ReservationResult(ReservationStatus.REJECTED, null, reason);
    }

    public boolean isApproved() {
        return status == ReservationStatus.APPROVED;
    }

    public boolean isRejected() {
        return status == ReservationStatus.REJECTED;
    }
}
