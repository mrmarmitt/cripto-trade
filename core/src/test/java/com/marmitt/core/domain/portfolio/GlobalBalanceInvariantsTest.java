package com.marmitt.core.domain.portfolio;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GlobalBalanceInvariantsTest {

    @Test
    void reserveMovesAmountFromAvailableToReserved() {
        GlobalBalance balance = newBalance("100");

        balance.reserve(new BigDecimal("30"));

        assertAmount("70", balance.getAvailableBalance());
        assertAmount("30", balance.getReservedBalance());
    }

    @Test
    void reserveRejectsInsufficientAvailableBalance() {
        GlobalBalance balance = newBalance("100");

        assertThrows(IllegalArgumentException.class, () -> balance.reserve(new BigDecimal("150")));
    }

    @Test
    void releaseRejectsAmountGreaterThanReserved() {
        GlobalBalance balance = newBalance("100");

        assertThrows(IllegalStateException.class, () -> balance.release(new BigDecimal("1")));
    }

    @Test
    void confirmExecutionRejectsAmountGreaterThanReserved() {
        GlobalBalance balance = newBalance("100");
        balance.reserve(new BigDecimal("15"));

        assertThrows(IllegalStateException.class,
                () -> balance.confirmExecution(new BigDecimal("20"), BigDecimal.ONE, BigDecimal.ZERO));
    }

    @Test
    void confirmExecutionUpdatesAvailableReservedRealizedAndFees() {
        GlobalBalance balance = newBalance("100");
        balance.reserve(new BigDecimal("25"));

        balance.confirmExecution(
                new BigDecimal("20"),
                new BigDecimal("2"),
                new BigDecimal("0.5")
        );

        assertAmount("97", balance.getAvailableBalance());
        assertAmount("5", balance.getReservedBalance());
        assertAmount("2", balance.getRealizedBalance());
        assertAmount("0.5", balance.getTotalFeesPaid());
    }

    private static GlobalBalance newBalance(String initialCapital) {
        return new GlobalBalance(UUID.randomUUID(), new BigDecimal(initialCapital), "USDT");
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}

