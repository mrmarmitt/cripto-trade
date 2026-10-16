package com.marmitt.core.enums;

import java.util.List;

/**
 * Status do ciclo de vida de uma transação/ordem
 */
public enum TransactionStatus {

    /**
     * Transação criada mas ainda não enviada para a exchange
     */
    PENDING,

    /**
     * Transação enviada para a exchange, aguardando confirmação
     */
    SUBMITTED,

    /**
     * Transação parcialmente executada.
     *
     * @deprecated Substituído por {@link #PARTIAL} no modelo alvo (F1-06). Removido em F1-08.
     */
    @Deprecated(forRemoval = true)
    PARTIALLY_FILLED,

    /**
     * Transação parcialmente executada — pelo menos um TransactionMatch existe.
     */
    PARTIAL,

    /**
     * Transação completamente executada com sucesso
     */
    FILLED,

    /**
     * Transação cancelada (manualmente ou por timeout)
     */
    CANCELED,

    /**
     * Transação rejeitada pela exchange (saldo insuficiente, etc.)
     */
    REJECTED,

    /**
     * Transação expirou sem ser executada
     */
    EXPIRED;

    /**
     * Ordem ainda viva na exchange (ou prestes a ser enviada): pode executar, ser cancelada e
     * precisa de recuperacao se o processo cair. Fonte unica para "em voo" (T45).
     */
    public static final List<TransactionStatus> IN_FLIGHT = List.of(PENDING, SUBMITTED, PARTIAL);

    public boolean isInFlight() {
        return IN_FLIGHT.contains(this);
    }

    /**
     * Verifica se o status é final (não haverá mais mudanças)
     */
    public boolean isFinal() {
        return this == FILLED ||
               this == CANCELED ||
               this == REJECTED ||
               this == EXPIRED;
    }

    /**
     * Verifica se a transação foi executada (total ou parcialmente)
     */
    @SuppressWarnings("deprecation")
    public boolean isExecuted() {
        return this == FILLED || this == PARTIALLY_FILLED || this == PARTIAL;
    }

    /**
     * Verifica se a transação falhou
     */
    public boolean isFailed() {
        return this == REJECTED || this == CANCELED || this == EXPIRED;
    }
}
