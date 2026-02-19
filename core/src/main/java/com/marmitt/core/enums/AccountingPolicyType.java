package com.marmitt.core.enums;

/**
 * Regra contábil — como vendas são casadas com compras para cálculo de P&L.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 3.5, Blueprint 2.B.2</a>
 */
public enum AccountingPolicyType {

    /**
     * First-In-First-Out — venda casa com a compra mais antiga
     */
    FIFO,

    /**
     * Last-In-First-Out — venda casa com a compra mais recente
     */
    LIFO,

    /**
     * Match específico — venda casa com um lote designado via targetLotId
     */
    SPECIFIC_MATCH
}
