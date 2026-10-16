package com.marmitt.core.application.usecase.runner.orderconciliation;

import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.capital.MarginRelease;
import com.marmitt.core.enums.OrderType;
import com.marmitt.core.enums.ReleaseReason;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.enums.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarginReleaseBuilderTest {

    private final MarginReleaseBuilder builder = new MarginReleaseBuilder();

    @Test
    void sellExpiredReleasesNoQuoteMargin() {
        // SELL nao reserva quote (bloqueia uma Position) -> terminar nao pode liberar margem,
        // senao subtrairia reserva de BUYs nao relacionados.
        assertTrue(builder.build(tx(TransactionType.SELL, TransactionStatus.EXPIRED)).isEmpty());
    }

    @Test
    void sellCanceledReleasesNoQuoteMargin() {
        assertTrue(builder.build(tx(TransactionType.SELL, TransactionStatus.CANCELED)).isEmpty());
    }

    @Test
    void buyExpiredStillReleasesFullReservedTotal() {
        Transaction buy = tx(TransactionType.BUY, TransactionStatus.EXPIRED);
        Optional<MarginRelease> release = builder.build(buy);
        assertTrue(release.isPresent());
        assertEquals(0, release.get().releaseAmount().compareTo(buy.getTotal()));
    }

    @Test
    void filledBuyBelowLimitReleasesReservedMinusExecutedValue() {
        // Reserva 0.0015 x 60000 = 90.00; executada a 59900 -> 89.85; excedente 0.15 volta ao disponivel.
        Transaction buy = filledTx(TransactionType.BUY, "59900.00000000");

        MarginRelease release = builder.buildFilledExcess(buy).orElseThrow();

        assertEquals(ReleaseReason.FILLED_EXCESS, release.reason());
        assertEquals(0, release.releaseAmount().compareTo(new BigDecimal("0.15000000")));
        assertEquals(0, release.executedAmount().compareTo(new BigDecimal("89.85000000")));
        assertEquals(buy.getId(), release.transactionId());
    }

    @Test
    void filledBuyAtLimitHasNoExcess() {
        assertTrue(builder.buildFilledExcess(filledTx(TransactionType.BUY, "60000.00000000")).isEmpty());
    }

    @Test
    void filledBuyAboveReservationReleasesNothing() {
        // Custo acima da reserva nao gera devolucao negativa (reserva insuficiente e outro problema).
        assertTrue(builder.buildFilledExcess(filledTx(TransactionType.BUY, "60100.00000000")).isEmpty());
    }

    @Test
    void filledSellNeverReleasesQuoteMargin() {
        assertTrue(builder.buildFilledExcess(filledTx(TransactionType.SELL, "59900.00000000")).isEmpty());
    }

    @Test
    void nonFilledBuyHasNoFilledExcess() {
        assertTrue(builder.buildFilledExcess(tx(TransactionType.BUY, TransactionStatus.PARTIAL)).isEmpty());
    }

    private Transaction filledTx(TransactionType type, String executedPrice) {
        return Transaction.reconstitute()
                .id(UUID.randomUUID())
                .runnerId(UUID.randomUUID())
                .clientOrderId("client-order-id")
                .status(TransactionStatus.FILLED)
                .type(type)
                .orderType(OrderType.LIMIT)
                .symbol("BTCUSDT")
                .quantity(new BigDecimal("0.00150000"))
                .price(new BigDecimal("60000.00000000"))
                .total(new BigDecimal("90.00000000"))
                .executedQuantity(new BigDecimal("0.00150000"))
                .executedPrice(new BigDecimal(executedPrice))
                .requestedAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private Transaction tx(TransactionType type, TransactionStatus status) {
        return Transaction.reconstitute()
                .id(UUID.randomUUID())
                .runnerId(UUID.randomUUID())
                .clientOrderId("client-order-id")
                .status(status)
                .type(type)
                .orderType(OrderType.LIMIT)
                .symbol("BTCUSDT")
                .quantity(new BigDecimal("0.00150000"))
                .price(new BigDecimal("60000.00000000"))
                .total(new BigDecimal("90.00000000"))
                .requestedAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
