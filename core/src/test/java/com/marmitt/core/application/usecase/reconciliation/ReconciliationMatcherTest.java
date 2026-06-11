package com.marmitt.core.application.usecase.reconciliation;

import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.reconciliation.ReconciliationReportDto;
import com.marmitt.core.dto.reconciliation.ReconciliationRequest;
import com.marmitt.core.dto.reconciliation.TradeExecutionDto;
import com.marmitt.core.enums.ReconciliationStatus;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.enums.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for ReconciliationMatcher — matching is keyed on exchangeOrderId because
 * Binance GET /api/v3/myTrades does not return clientOrderId in the response payload.
 */
class ReconciliationMatcherTest {

    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-01-02T00:00:00Z");
    private static final Instant FILL_AT = Instant.parse("2026-01-01T10:00:00Z");
    private static final long ORDER_ID = 100234L;

    @Test
    void matchReturnsMatchedWhenQtyAndQuoteMatchWithinTolerance() {
        // Exchange fill and local tx share the same orderId — both qty and quote match
        ReconciliationReportDto report = ReconciliationMatcher.match(
                List.of(fill(ORDER_ID, "0.01", "500.00")),
                List.of(localTx("client-1", ORDER_ID, "0.01", "50000", "500.00")),
                request(true));

        assertEquals(1, report.summary().matched());
        assertEquals(0, report.summary().divergent());
        assertEquals(1, report.entries().size());
        assertEquals(ReconciliationStatus.MATCHED, report.entries().get(0).status());
        assertEquals("client-1", report.entries().get(0).clientOrderId());
    }

    @Test
    void matchReturnsDivergentWhenQtyDiffersAboveTolerance() {
        ReconciliationReportDto report = ReconciliationMatcher.match(
                List.of(fill(ORDER_ID, "0.01", "500.00")),
                List.of(localTx("client-1", ORDER_ID, "0.02", "50000", "1000.00")),
                request(true));

        assertEquals(1, report.summary().divergent());
        assertEquals(ReconciliationStatus.DIVERGENT, report.entries().get(0).status());
    }

    @Test
    void matchReturnsDivergentWhenQtyMatchesButQuoteDiffersAboveTolerance() {
        // Same qty, different execution price → quote diverges
        ReconciliationReportDto report = ReconciliationMatcher.match(
                List.of(fill(ORDER_ID, "0.01", "500.00")),           // exchange: 500 USDT
                List.of(localTx("client-1", ORDER_ID, "0.01", "60000", "600.00")),  // local: 600 USDT
                request(true));

        assertEquals(1, report.summary().divergent());
    }

    @Test
    void matchReturnsDivergentWhenSideDiffers() {
        // Exchange fill is BUY but local transaction is SELL → side mismatch
        TradeExecutionDto buyFill = new TradeExecutionDto(
                "t1", ORDER_ID, null, "BTCUSDT",
                new BigDecimal("50000"), new BigDecimal("0.01"), new BigDecimal("500"),
                new BigDecimal("0.0001"), "BNB", FILL_AT, true /* isBuy */);
        Transaction sellLocal = Transaction.reconstitute()
                .id(UUID.randomUUID())
                .runnerId(UUID.randomUUID())
                .clientOrderId("client-1")
                .exchangeOrderId(String.valueOf(ORDER_ID))
                .status(TransactionStatus.FILLED)
                .type(TransactionType.SELL)   // opposite side
                .symbol("BTCUSDT")
                .quantity(new BigDecimal("0.01"))
                .executedQuantity(new BigDecimal("0.01"))
                .price(new BigDecimal("50000"))
                .executedPrice(new BigDecimal("50000"))
                .total(new BigDecimal("500"))
                .confidence(null).reasoning(null).targetLotId(null)
                .requestedAt(FILL_AT).updatedAt(FILL_AT).executedAt(FILL_AT)
                .rejectReason(null).version(0L)
                .build();

        ReconciliationReportDto report = ReconciliationMatcher.match(
                List.of(buyFill), List.of(sellLocal), request(true));

        assertEquals(1, report.summary().divergent());
        assertEquals(ReconciliationStatus.DIVERGENT, report.entries().get(0).status());
    }

    @Test
    void matchReturnsMatchedWhenQtyAndQuoteAreBothWithinTolerance() {
        // qty diff = 0.000001 (≤ QTY_TOLERANCE), quote diff = 0.005 (< QUOTE_TOLERANCE 0.01)
        ReconciliationReportDto report = ReconciliationMatcher.match(
                List.of(fill(ORDER_ID, "0.010001", "500.005")),
                List.of(localTx("client-1", ORDER_ID, "0.010000", "50000", "500.00")),
                request(true));

        assertEquals(1, report.summary().matched());
    }

    @Test
    void matchReturnsExchangeOnlyWhenNoLocalTransactionForOrderId() {
        ReconciliationReportDto report = ReconciliationMatcher.match(
                List.of(fill(ORDER_ID, "0.01", "500.00")),
                List.of(),
                request(true));

        assertEquals(1, report.summary().exchangeOnly());
        assertEquals(ReconciliationStatus.EXCHANGE_ONLY, report.entries().get(0).status());
    }

    @Test
    void matchExchangeOnlyEntryUsesClientOrderIdFromFillWhenAvailable() {
        TradeExecutionDto fillWithClientId = new TradeExecutionDto(
                "t1", ORDER_ID, "client-from-exchange", "BTCUSDT",
                new BigDecimal("50000"), new BigDecimal("0.01"), new BigDecimal("500"),
                new BigDecimal("0.0001"), "BNB", FILL_AT, true);

        ReconciliationReportDto report = ReconciliationMatcher.match(
                List.of(fillWithClientId), List.of(), request(true));

        assertEquals("client-from-exchange", report.entries().get(0).clientOrderId());
    }

    @Test
    void matchExchangeOnlyEntryFallsBackToOrderIdLabelWhenClientOrderIdIsNull() {
        // Binance does not return clientOrderId in myTrades — null is the expected real-world case
        TradeExecutionDto fillNullClientId = new TradeExecutionDto(
                "t1", ORDER_ID, null, "BTCUSDT",
                new BigDecimal("50000"), new BigDecimal("0.01"), new BigDecimal("500"),
                new BigDecimal("0.0001"), "BNB", FILL_AT, true);

        ReconciliationReportDto report = ReconciliationMatcher.match(
                List.of(fillNullClientId), List.of(), request(true));

        assertEquals("order-" + ORDER_ID, report.entries().get(0).clientOrderId());
    }

    @Test
    void matchReturnsLocalOnlyWhenNoExchangeFillForExchangeOrderId() {
        ReconciliationReportDto report = ReconciliationMatcher.match(
                List.of(),
                List.of(localTx("client-1", ORDER_ID, "0.01", "50000", "500.00")),
                request(true));

        assertEquals(1, report.summary().localOnly());
        assertEquals(ReconciliationStatus.LOCAL_ONLY, report.entries().get(0).status());
        assertEquals("client-1", report.entries().get(0).clientOrderId());
    }

    @Test
    void matchReturnsLocalOnlyForNeverSubmittedTransaction() {
        // Transaction with no exchangeOrderId was never successfully submitted
        Transaction neverSubmitted = localTxNoExchangeOrderId("client-pending");

        ReconciliationReportDto report = ReconciliationMatcher.match(
                List.of(), List.of(neverSubmitted), request(true));

        assertEquals(1, report.summary().localOnly());
        assertEquals("client-pending", report.entries().get(0).clientOrderId());
    }

    @Test
    void matchExcludesMatchedEntriesFromListWhenIncludeMatchedFalse() {
        ReconciliationReportDto report = ReconciliationMatcher.match(
                List.of(fill(ORDER_ID, "0.01", "500.00")),
                List.of(localTx("client-1", ORDER_ID, "0.01", "50000", "500.00")),
                request(false));

        assertEquals(1, report.summary().matched());
        assertTrue(report.entries().isEmpty());
    }

    @Test
    void matchIncludesMatchedEntriesWhenIncludeMatchedTrue() {
        ReconciliationReportDto report = ReconciliationMatcher.match(
                List.of(fill(ORDER_ID, "0.01", "500.00")),
                List.of(localTx("client-1", ORDER_ID, "0.01", "50000", "500.00")),
                request(true));

        assertEquals(1, report.summary().matched());
        assertEquals(1, report.entries().size());
    }

    @Test
    void matchAggregatesMultipleFillsForSameExchangeOrderId() {
        long orderId = 100234L;
        TradeExecutionDto fill1 = new TradeExecutionDto(
                "t1", orderId, null, "BTCUSDT",
                new BigDecimal("50000"), new BigDecimal("0.005"), new BigDecimal("250"),
                new BigDecimal("0.0001"), "BNB", FILL_AT, true);
        TradeExecutionDto fill2 = new TradeExecutionDto(
                "t2", orderId, null, "BTCUSDT",
                new BigDecimal("50000"), new BigDecimal("0.005"), new BigDecimal("250"),
                new BigDecimal("0.0001"), "BNB", FILL_AT.plusSeconds(60), true);

        ReconciliationReportDto report = ReconciliationMatcher.match(
                List.of(fill1, fill2),
                List.of(localTx("client-1", orderId, "0.01", "50000", "500.00")),
                request(true));

        assertEquals(1, report.summary().matched());
        assertEquals(2, report.entries().get(0).exchangeSide().fillCount());
    }

    @Test
    void matchSummaryTotalEqualsAllCategorySum() {
        long orderId2 = 200001L;
        ReconciliationReportDto report = ReconciliationMatcher.match(
                List.of(fill(ORDER_ID, "0.01", "500.00"), fill(orderId2, "0.02", "1000.00")),
                List.of(
                        localTx("client-matched", ORDER_ID, "0.01", "50000", "500.00"),
                        localTx("client-local-only", 300001L, "0.03", "50000", "1500.00")),
                request(true));

        assertEquals(1, report.summary().matched());
        assertEquals(1, report.summary().exchangeOnly());
        assertEquals(1, report.summary().localOnly());
        assertEquals(3, report.summary().total());
    }

    @Test
    void matchReturnsEmptyReportWhenBothListsAreEmpty() {
        ReconciliationReportDto report = ReconciliationMatcher.match(List.of(), List.of(), request(true));

        assertEquals(0, report.summary().total());
        assertTrue(report.entries().isEmpty());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static TradeExecutionDto fill(long exchangeOrderId, String qty, String quoteQty) {
        return new TradeExecutionDto(
                "trade-1", exchangeOrderId,
                null,       // clientOrderId not returned by Binance myTrades
                "BTCUSDT",
                new BigDecimal("50000"),
                new BigDecimal(qty),
                new BigDecimal(quoteQty),
                new BigDecimal("0.0001"), "BNB", FILL_AT, true);
    }

    private static Transaction localTx(String clientOrderId, long exchangeOrderId,
                                       String qty, String execPrice, String total) {
        BigDecimal amount = new BigDecimal(qty);
        return Transaction.reconstitute()
                .id(UUID.randomUUID())
                .runnerId(UUID.randomUUID())
                .clientOrderId(clientOrderId)
                .exchangeOrderId(String.valueOf(exchangeOrderId))
                .status(TransactionStatus.FILLED)
                .type(TransactionType.BUY)
                .symbol("BTCUSDT")
                .quantity(amount)
                .executedQuantity(amount)
                .price(new BigDecimal(execPrice))
                .executedPrice(new BigDecimal(execPrice))
                .total(new BigDecimal(total))
                .confidence(null)
                .reasoning(null)
                .targetLotId(null)
                .requestedAt(FILL_AT)
                .updatedAt(FILL_AT)
                .executedAt(FILL_AT)
                .rejectReason(null)
                .version(0L)
                .build();
    }

    private static Transaction localTxNoExchangeOrderId(String clientOrderId) {
        return Transaction.reconstitute()
                .id(UUID.randomUUID())
                .runnerId(UUID.randomUUID())
                .clientOrderId(clientOrderId)
                .exchangeOrderId(null)
                .status(TransactionStatus.PENDING)
                .type(TransactionType.BUY)
                .symbol("BTCUSDT")
                .quantity(new BigDecimal("0.01"))
                .executedQuantity(null)
                .price(new BigDecimal("50000"))
                .executedPrice(null)
                .total(new BigDecimal("500"))
                .confidence(null)
                .reasoning(null)
                .targetLotId(null)
                .requestedAt(FILL_AT)
                .updatedAt(FILL_AT)
                .executedAt(null)
                .rejectReason(null)
                .version(0L)
                .build();
    }

    private static ReconciliationRequest request(boolean includeMatched) {
        return new ReconciliationRequest("BTCUSDT", FROM, TO, includeMatched);
    }
}
