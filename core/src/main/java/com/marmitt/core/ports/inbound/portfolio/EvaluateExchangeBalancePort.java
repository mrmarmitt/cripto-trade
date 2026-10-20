package com.marmitt.core.ports.inbound.portfolio;

/**
 * Avaliacao periodica da conferencia de saldo por exchange durante a operacao (T57): falta na conta alem
 * das ordens em voo poe em HALT os portfolios da exchange. Complementa a conferencia do boot.
 */
public interface EvaluateExchangeBalancePort {

    void evaluate();
}
