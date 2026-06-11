package com.marmitt.core.application.usecase.reconciliation;

import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.enums.TransactionType;
import com.marmitt.core.dto.reconciliation.ExchangeSideDto;
import com.marmitt.core.dto.reconciliation.LocalSideDto;
import com.marmitt.core.dto.reconciliation.ReconciliationEntryDto;
import com.marmitt.core.dto.reconciliation.ReconciliationReportDto;
import com.marmitt.core.dto.reconciliation.ReconciliationRequest;
import com.marmitt.core.dto.reconciliation.ReconciliationSummaryDto;
import com.marmitt.core.dto.reconciliation.TradeExecutionDto;
import com.marmitt.core.enums.ReconciliationStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class ReconciliationMatcher {

    private static final BigDecimal QTY_TOLERANCE = new BigDecimal("0.000001");
    // Absolute tolerance for quote value comparison (e.g. USDT rounding differences from partial fills)
    private static final BigDecimal QUOTE_TOLERANCE = new BigDecimal("0.01");

    private ReconciliationMatcher() {}

    static ReconciliationReportDto match(
            List<TradeExecutionDto> exchangeFills,
            List<Transaction> localTransactions,
            ReconciliationRequest request) {

        // Binance GET /api/v3/myTrades does not return clientOrderId; group by orderId (always present).
        Map<String, List<TradeExecutionDto>> fillsByOrderId = exchangeFills.stream()
                .collect(Collectors.groupingBy(
                        f -> String.valueOf(f.exchangeOrderId()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        // Index submitted local transactions by their exchangeOrderId (set on order submission).
        // Transactions without an exchangeOrderId were never successfully submitted.
        Map<String, Transaction> localByOrderId = new LinkedHashMap<>();
        List<Transaction> neverSubmitted = new ArrayList<>();
        for (Transaction t : localTransactions) {
            if (t.getExchangeOrderId() != null && !t.getExchangeOrderId().isBlank()) {
                localByOrderId.put(t.getExchangeOrderId(), t);
            } else {
                neverSubmitted.add(t);
            }
        }

        List<ReconciliationEntryDto> entries = new ArrayList<>();
        int matched = 0, divergent = 0, exchangeOnly = 0, localOnly = 0;

        for (Map.Entry<String, List<TradeExecutionDto>> e : fillsByOrderId.entrySet()) {
            ExchangeSideDto exchangeSide = aggregate(e.getValue());
            Transaction local = localByOrderId.remove(e.getKey());

            if (local == null) {
                exchangeOnly++;
                // Use exchange clientOrderId when available (e.g. some exchange environments return it)
                String displayId = e.getValue().get(0).clientOrderId() != null
                        ? e.getValue().get(0).clientOrderId()
                        : "order-" + e.getKey();
                entries.add(new ReconciliationEntryDto(
                        ReconciliationStatus.EXCHANGE_ONLY, displayId, exchangeSide, null));
            } else {
                LocalSideDto localSide = toLocalSide(local);

                BigDecimal localQty = local.getExecutedQuantity() != null
                        ? local.getExecutedQuantity()
                        : BigDecimal.ZERO;
                boolean qtyMatch = exchangeSide.executedQty().subtract(localQty).abs()
                        .compareTo(QTY_TOLERANCE) <= 0;

                // Compare executed quote value to catch price discrepancies (same qty, wrong price)
                BigDecimal localExecValue = local.getExecutedPrice() != null && local.getExecutedQuantity() != null
                        ? local.getExecutedPrice().multiply(local.getExecutedQuantity())
                        : local.getTotal();
                boolean quoteMatch = exchangeSide.totalQuote().subtract(localExecValue).abs()
                        .compareTo(QUOTE_TOLERANCE) <= 0;

                // Compare trade side — a BUY fill against a SELL local record is a critical discrepancy
                boolean sideMatch = exchangeSide.isBuy() == (local.getType() == TransactionType.BUY);

                ReconciliationStatus status = qtyMatch && quoteMatch && sideMatch
                        ? ReconciliationStatus.MATCHED
                        : ReconciliationStatus.DIVERGENT;

                if (status == ReconciliationStatus.MATCHED) matched++;
                else divergent++;

                if (status != ReconciliationStatus.MATCHED || request.includeMatched()) {
                    entries.add(new ReconciliationEntryDto(
                            status, local.getClientOrderId(), exchangeSide, localSide));
                }
            }
        }

        // Submitted local transactions with no matching exchange fill
        for (Transaction local : localByOrderId.values()) {
            localOnly++;
            entries.add(new ReconciliationEntryDto(
                    ReconciliationStatus.LOCAL_ONLY, local.getClientOrderId(), null, toLocalSide(local)));
        }

        // Never-submitted local transactions (no exchangeOrderId) — always LOCAL_ONLY
        for (Transaction local : neverSubmitted) {
            localOnly++;
            entries.add(new ReconciliationEntryDto(
                    ReconciliationStatus.LOCAL_ONLY, local.getClientOrderId(), null, toLocalSide(local)));
        }

        int total = matched + divergent + exchangeOnly + localOnly;
        ReconciliationSummaryDto summary = new ReconciliationSummaryDto(
                total, matched, divergent, exchangeOnly, localOnly);

        return new ReconciliationReportDto(
                request.symbol(), request.from(), request.to(), summary, entries);
    }

    private static ExchangeSideDto aggregate(List<TradeExecutionDto> fills) {
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalQuote = BigDecimal.ZERO;
        BigDecimal totalFees = BigDecimal.ZERO;
        String feeAsset = null;
        Instant firstFill = null;
        Instant lastFill = null;
        long orderId = 0;
        boolean isBuy = fills.get(0).isBuy(); // all fills for an order share the same side

        for (TradeExecutionDto f : fills) {
            totalQty = totalQty.add(f.qty());
            totalQuote = totalQuote.add(f.quoteQty());
            if (f.commission() != null) totalFees = totalFees.add(f.commission());
            if (feeAsset == null && f.commissionAsset() != null) feeAsset = f.commissionAsset();
            if (firstFill == null || f.executedAt().isBefore(firstFill)) firstFill = f.executedAt();
            if (lastFill == null || f.executedAt().isAfter(lastFill)) lastFill = f.executedAt();
            orderId = f.exchangeOrderId();
        }

        BigDecimal avgPrice = totalQty.compareTo(BigDecimal.ZERO) > 0
                ? totalQuote.divide(totalQty, 8, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new ExchangeSideDto(
                orderId, fills.size(), totalQty, avgPrice,
                totalQuote, totalFees, feeAsset, firstFill, lastFill, isBuy);
    }

    private static LocalSideDto toLocalSide(Transaction t) {
        return new LocalSideDto(
                t.getId(),
                t.getRunnerId(),
                t.getType(),
                t.getStatus(),
                t.getExecutedQuantity(),
                t.getExecutedPrice(),
                t.getTotal(),
                t.getRequestedAt(),
                t.getUpdatedAt());
    }
}
