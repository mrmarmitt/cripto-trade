package com.marmitt.core.domain.runner;

import com.marmitt.core.enums.PositionStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T44: limites do lote avaliados pelo core sem a estrategia.
 */
class PositionProtectionTest {

    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");

    /** 0.001 BTC por 65 USDT de custo (preco medio 65000). */
    private final Position lot = lot(NOW.minus(Duration.ofHours(1)));

    @Test
    void lossBelowTheLimitKeepsTheLot() {
        PositionProtection protection = PositionProtection.defaults();

        assertTrue(protection.violation(lot, new BigDecimal("61750.01"), NOW).isEmpty(), "perda de 4.99..%");
        assertTrue(protection.violation(lot, new BigDecimal("70000"), NOW).isEmpty(), "lote no lucro");
    }

    @Test
    void lossAtTheLimitTriggersMaxLoss() {
        PositionProtection protection = PositionProtection.defaults();

        assertEquals(PositionProtection.Reason.MAX_LOSS,
                protection.violation(lot, new BigDecimal("61750"), NOW).orElseThrow().reason(), "perda de 5% exatos");
        assertEquals(PositionProtection.Reason.MAX_LOSS,
                protection.violation(lot, new BigDecimal("50000"), NOW).orElseThrow().reason());
    }

    @Test
    void lossIsMeasuredAgainstTheCostPaidIncludingFees() {
        // Fee no ativo base: recebeu 0.000999 BTC pagando 65 USDT (custo efetivo ~65065/BTC).
        Position feeReduced = Position.reconstitute()
                .id(UUID.randomUUID()).runnerId(UUID.randomUUID()).symbol("BTCUSDT")
                .status(PositionStatus.OPEN).quantity(new BigDecimal("0.000999")).averagePrice(new BigDecimal("65065.07"))
                .costBasis(new BigDecimal("65")).realizedPnl(BigDecimal.ZERO).openedAt(NOW).updatedAt(NOW).build();

        assertTrue(PositionProtection.defaults().violation(feeReduced, new BigDecimal("61811.81"), NOW).isPresent(),
                "0.000999 x 61811.81 = 61.7499.. < 95% do custo de 65");
    }

    @Test
    void holdingLimitIsOffByDefaultAndTriggersWhenSet() {
        Position old = lot(NOW.minus(Duration.ofDays(30)));
        assertTrue(PositionProtection.defaults().violation(old, new BigDecimal("65000"), NOW).isEmpty());

        PositionProtection withHolding = new PositionProtection(new BigDecimal("0.05"), Duration.ofHours(24));
        assertEquals(PositionProtection.Reason.MAX_HOLDING,
                withHolding.violation(old, new BigDecimal("65000"), NOW).orElseThrow().reason());
        assertTrue(withHolding.violation(lot, new BigDecimal("65000"), NOW).isEmpty(), "lote de 1h");
    }

    @Test
    void lossWinsWhenBothLimitsAreViolated() {
        PositionProtection withHolding = new PositionProtection(new BigDecimal("0.05"), Duration.ofHours(24));

        assertEquals(PositionProtection.Reason.MAX_LOSS, withHolding.violation(
                lot(NOW.minus(Duration.ofDays(2))), new BigDecimal("50000"), NOW).orElseThrow().reason());
    }

    @Test
    void outOfRangeLimitsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PositionProtection(BigDecimal.ZERO, null));
        assertThrows(IllegalArgumentException.class, () -> new PositionProtection(BigDecimal.ONE, null));
        assertThrows(NullPointerException.class, () -> new PositionProtection(null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new PositionProtection(new BigDecimal("0.05"), Duration.ofMillis(10)));
    }

    private static Position lot(Instant openedAt) {
        return Position.reconstitute()
                .id(UUID.randomUUID()).runnerId(UUID.randomUUID()).symbol("BTCUSDT")
                .status(PositionStatus.OPEN).quantity(new BigDecimal("0.001")).averagePrice(new BigDecimal("65000"))
                .costBasis(new BigDecimal("65")).realizedPnl(BigDecimal.ZERO).openedAt(openedAt).updatedAt(openedAt)
                .build();
    }
}
