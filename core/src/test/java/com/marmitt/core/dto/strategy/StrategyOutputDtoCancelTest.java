package com.marmitt.core.dto.strategy;

import com.marmitt.core.enums.TradingAction;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyOutputDtoCancelTest {

    @Test
    void cancelFactoryProducesCancelDecisionWithTarget() {
        UUID target = UUID.randomUUID();
        StrategyOutputDto out = StrategyOutputDto.cancel("sma", target, "thesis changed");

        assertEquals(TradingAction.SHOULD_CANCEL, out.decision());
        assertEquals(target, out.targetTransactionId());
        assertTrue(out.shouldCancel());
        assertFalse(out.shouldTrade(), "cancel is not a BUY/SELL trade");
        assertFalse(out.shouldHold());
    }

    @Test
    void cancelRequiresTargetTransactionId() {
        assertThrows(NullPointerException.class, () -> StrategyOutputDto.cancel("sma", null, "x"));
    }
}
