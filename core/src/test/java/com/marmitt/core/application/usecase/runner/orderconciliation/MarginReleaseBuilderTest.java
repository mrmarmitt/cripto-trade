package com.marmitt.core.application.usecase.runner.orderconciliation;

import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.capital.MarginRelease;
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

    private Transaction tx(TransactionType type, TransactionStatus status) {
        return Transaction.reconstitute()
                .id(UUID.randomUUID())
                .runnerId(UUID.randomUUID())
                .clientOrderId("client-order-id")
                .status(status)
                .type(type)
                .symbol("BTCUSDT")
                .quantity(new BigDecimal("0.00150000"))
                .price(new BigDecimal("60000.00000000"))
                .total(new BigDecimal("90.00000000"))
                .requestedAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
