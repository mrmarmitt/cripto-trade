package com.marmitt.core.enums;

/**
 * Motivo da devolução de margem reservada via {@code CapitalManager.release()}.
 * Determina o tipo de estorno contábil aplicado ao GlobalBalance.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 5.2.3</a>
 */
public enum ReleaseReason {

    /**
     * Portfolio recusou o Capital Request ou exchange rejeitou a ordem.
     * executedAmount = 0 → estorno total da reserva.
     */
    REJECTED,

    /**
     * Ordem cancelada pelo Runner, operador ou exchange após submissão.
     * executedAmount pode ser {@literal >} 0 (caso parcialmente executado antes do cancelamento).
     */
    CANCELED,

    /**
     * Ordem expirou (timeout do Watchdog) ou intenção não materializada (crash recovery).
     * executedAmount = 0 → estorno total.
     */
    EXPIRED
}
