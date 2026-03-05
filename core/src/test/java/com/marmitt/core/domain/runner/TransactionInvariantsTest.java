package com.marmitt.core.domain.runner;

import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.enums.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionInvariantsTest {

    @Test
    void submitTransitionsFromPendingAndRejectsDuplicateSubmit() {
        Transaction transaction = newTransaction();

        transaction.submit("EX_ORDER_1");

        assertEquals(TransactionStatus.SUBMITTED, transaction.getStatus());
        assertEquals("EX_ORDER_1", transaction.getExchangeOrderId());
        assertThrows(IllegalStateException.class, () -> transaction.submit("EX_ORDER_2"));
    }

    @Test
    void fillTransitionsToFinalAndBlocksFurtherNonTerminalTransitions() {
        Transaction transaction = newTransaction();
        transaction.submit("EX_ORDER_1");

        transaction.fill(new BigDecimal("2.5"), new BigDecimal("10"));

        assertEquals(TransactionStatus.FILLED, transaction.getStatus());
        assertNotNull(transaction.getExecutedAt());
        assertThrows(IllegalStateException.class, () ->
                transaction.partialFill(new BigDecimal("2.5"), new BigDecimal("10")));
        assertThrows(IllegalStateException.class, transaction::cancel);
    }

    @Test
    void expireRejectsInvalidTransitionFromPartial() {
        Transaction transaction = newTransaction();
        transaction.submit("EX_ORDER_1");
        transaction.partialFill(new BigDecimal("1.0"), new BigDecimal("9.5"));

        assertThrows(IllegalStateException.class, transaction::expire);
    }

    @Test
    void executedValueIsZeroBeforeExecution() {
        Transaction transaction = newTransaction();

        assertAmount("0", transaction.getExecutedValue());
    }

    @Test
    void executedValueUsesAccumulatedExecution() {
        Transaction transaction = newTransaction();
        transaction.submit("EX_ORDER_1");
        transaction.partialFill(new BigDecimal("2.0"), new BigDecimal("11.5"));

        assertAmount("23.00000000", transaction.getExecutedValue());
    }

    private static Transaction newTransaction() {
        BigDecimal quantity = new BigDecimal("2.5");
        BigDecimal price = new BigDecimal("10");
        BigDecimal total = quantity.multiply(price);
        return new Transaction(
                UUID.randomUUID(),
                "client-order-id-1",
                TransactionType.BUY,
                "BTCUSDT",
                quantity,
                price,
                total,
                new BigDecimal("0.8"),
                "test",
                null
        );
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}

