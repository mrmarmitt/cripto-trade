package com.marmitt.core.enums;

/**
 * Resultado de uma solicitação de reserva de capital via {@code CapitalManager.reserve()}.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 5.2.1</a>
 */
public enum ReservationStatus {

    /** Capital reservado com sucesso. O Runner pode prosseguir com o Order Dispatch. */
    APPROVED,

    /** Reserva negada. O Runner deve descartar o sinal. */
    REJECTED
}
