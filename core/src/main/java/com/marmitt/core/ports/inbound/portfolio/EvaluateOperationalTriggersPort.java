package com.marmitt.core.ports.inbound.portfolio;

/**
 * Avaliação periódica dos gatilhos operacionais de Safe Mode por estado (T25): hoje, DLQ acumulando.
 * Os gatilhos por evento (watchdog travado) disparam onde o evento ocorre; a divergência de saldo tem
 * avaliação própria ({@link EvaluateExchangeBalancePort}, T57).
 */
public interface EvaluateOperationalTriggersPort {

    void evaluate();
}
