package com.marmitt.core.dto.runner;

import java.util.Objects;

/**
 * Resposta síncrona do {@code OrderDispatchPort} após tentativa de envio de ordem.
 * <p>
 * Três outcomes possíveis:
 * <ul>
 *   <li>{@link AckStatus#ACCEPTED} — exchange aceitou a ordem; {@code exchangeOrderId} não é null.</li>
 *   <li>{@link AckStatus#REJECTED} — exchange rejeitou a ordem (inválida, fora de limites, etc.);
 *       {@code rejectionReason} explica o motivo.</li>
 *   <li>{@link AckStatus#TIMEOUT} — não foi possível confirmar aceitação dentro do SLA;
 *       a Transaction permanece PENDING para reconciliação no Boot Sequence (F2-06).</li>
 * </ul>
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 6.2.1, 6.3.2</a>
 */
public record OrderAck(
        AckStatus status,
        String exchangeOrderId,
        String rejectionReason
) {

    public enum AckStatus {
        ACCEPTED,
        REJECTED,
        TIMEOUT
    }

    public OrderAck {
        Objects.requireNonNull(status, "status cannot be null");
    }

    /** Exchange aceitou a ordem e atribuiu um ID. */
    public static OrderAck accepted(String exchangeOrderId) {
        Objects.requireNonNull(exchangeOrderId, "exchangeOrderId cannot be null");
        return new OrderAck(AckStatus.ACCEPTED, exchangeOrderId, null);
    }

    /** Exchange rejeitou explicitamente a ordem. */
    public static OrderAck rejected(String reason) {
        Objects.requireNonNull(reason, "reason cannot be null");
        return new OrderAck(AckStatus.REJECTED, null, reason);
    }

    /** Envio não confirmado dentro do SLA — deixar para reconciliação. */
    public static OrderAck timeout() {
        return new OrderAck(AckStatus.TIMEOUT, null, null);
    }

    public boolean isAccepted() { return status == AckStatus.ACCEPTED; }
    public boolean isRejected() { return status == AckStatus.REJECTED; }
    public boolean isTimeout()  { return status == AckStatus.TIMEOUT; }
}
