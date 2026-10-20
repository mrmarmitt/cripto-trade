package com.marmitt.core.ports.inbound.portfolio;

/**
 * Avaliação periódica dos gatilhos operacionais de Safe Mode por estado (T25): hoje, DLQ acumulando.
 * Os gatilhos por evento (divergência de saldo no boot, watchdog travado) disparam onde o evento ocorre.
 */
public interface EvaluateOperationalTriggersPort {

    void evaluate();
}
