package com.marmitt.strategyservice.wire;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T41: a decisao remota e validada com as mesmas regras do openapi.yaml.
 */
class DecisionValidatorTest {

    @Test
    void validVariantsPass() {
        assertTrue(DecisionValidator.violations(decision("SHOULD_HOLD", null, null, null, null)).isEmpty());
        assertTrue(DecisionValidator.violations(decision("SHOULD_BUY", "0.5", "0.001", "65000", null)).isEmpty());
        assertTrue(DecisionValidator.violations(decision("SHOULD_SELL", "1", "0.001", null, null)).isEmpty());
        assertTrue(DecisionValidator.violations(
                decision("SHOULD_CANCEL", null, null, null, "11111111-1111-1111-1111-111111111111")).isEmpty());
    }

    @Test
    void outOfContractDecisionsAreRejected() {
        assertFalse(DecisionValidator.violations(decision("SHOULD_BUY", "0.5", null, null, null)).isEmpty(),
                "BUY sem quantity");
        assertFalse(DecisionValidator.violations(decision("SHOULD_BUY", "1.5", "0.001", null, null)).isEmpty(),
                "confidence fora de [0,1]");
        assertFalse(DecisionValidator.violations(decision("SHOULD_SELL", "0.5", "-0.1", null, null)).isEmpty(),
                "quantity negativa");
        assertFalse(DecisionValidator.violations(decision("SHOULD_BUY", "0.5", "0.001", "0", null)).isEmpty(),
                "limitPrice zero");
        assertFalse(DecisionValidator.violations(decision("SHOULD_CANCEL", null, null, null, null)).isEmpty(),
                "CANCEL sem alvo");
        assertFalse(DecisionValidator.violations(decision("SHOULD_PANIC", null, null, null, null)).isEmpty(),
                "decision desconhecida");
        assertFalse(DecisionValidator.violations(decision("SHOULD_BUY", "0.5", "1e3", null, null)).isEmpty(),
                "notacao cientifica nao e decimal do contrato");
        assertFalse(DecisionValidator.violations(new WireTypes.Decision("SMA", "SHOULD_HOLD", null, null, null,
                null, null, "why", "not-a-time", null)).isEmpty(), "timestamp fora do ISO-8601");
    }

    private static WireTypes.Decision decision(String action, String confidence, String quantity,
                                               String limitPrice, String targetTransactionId) {
        return new WireTypes.Decision("SMA", action, confidence, quantity, limitPrice, null,
                targetTransactionId, "why", null, null);
    }
}
