package com.marmitt.core.enums;

/**
 * Resultado da conferencia de saldo de uma exchange (T57): o que os portfolios do ctrade esperam ter na
 * conta contra o que a conta tem. Declarado em ordem crescente de severidade.
 */
public enum ExchangeBalanceStatus {
    /** A conta tem pelo menos o esperado (o excedente e saldo fora de qualquer portfolio). */
    OK,
    /** Falta na conta, mas cabe nas ordens em voo: fill na exchange ainda nao conciliado no ctrade. */
    PENDING,
    /** Exchange sem consulta de conta: nao conferido. */
    SKIPPED,
    /** Nao foi possivel conferir (consulta falhou, saldo local ausente). */
    FAILED,
    /** O ctrade espera mais do que a conta tem, alem das ordens em voo. */
    DEFICIT
}
