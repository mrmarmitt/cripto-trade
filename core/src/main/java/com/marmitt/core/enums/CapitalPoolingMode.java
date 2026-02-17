package com.marmitt.core.enums;

/**
 * Modo de visibilidade de capital entre Runners de um Portfolio.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 3.5, Blueprint 11.2.C</a>
 */
public enum CapitalPoolingMode {

    /**
     * Todos os Runners competem pelo saldo disponível do GlobalBalance
     */
    SHARED,

    /**
     * Cada Runner tem uma fatia fixa (dedicatedBudget) reservada
     */
    DEDICATED
}
