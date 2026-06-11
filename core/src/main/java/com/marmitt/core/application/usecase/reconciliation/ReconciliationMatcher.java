package com.marmitt.core.application.usecase.reconciliation;

import com.marmitt.core.domain.runner.Transaction;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class ReconciliationMatcher {

    private static final BigDecimal QTY_TOLERANCE = new BigDecimal("0.000001");

    private ReconciliationMatcher() {}

    static ReconciliationReportDto match(
            List<TradeExecutionDto> exchangeFills,
            List<Transaction> localTransactions,
            ReconciliationRequest request) {

        Map<String, List<TradeExecutionDto>> fillsByClientOrderId = exchangeFills.stream()
                .filter(f -> f.clientOrderId() != null && !f.clientOrderId().isBlank())
                .collect(Collectors.groupingBy(
                        TradeExecutionDto::clientOrderId,
                        LinkedHashMap::new,
                        Collectors.toList()));

        Map<String, Transaction> localByClientOrderId = localTransactions.stream()
                .collect(Collectors.toMap(Transaction::getClientOrderId, t -> t, (a, b) -> a, LinkedHashMap::new));

        List<ReconciliationEntryDto> entries = new ArrayList<>();
        int matched = 0, divergent = 0, exchangeOnly = 0, localOnly = 0;

        for (Map.Entry<String, List<TradeExecutionDto>> e : fillsByClientOrderId.entrySet()) {
            String clientOrderId = e.getKey();
            ExchangeSideDto exchangeSide = aggregate(e.getValue());
            Transaction local = localByClientOrderId.remove(clientOrderId);

            if (local == null) {
                exchangeOnly++;
                entries.add(new ReconciliationEntryDto(
                        ReconciliationStatus.EXCHANGE_ONLY, clientOrderId, exchangeSide, null));
            } else {
                LocalSideDto localSide = toLocalSide(local);
                BigDecimal localQty = local.getExecutedQuantity() != null
                        ? local.getExecutedQuantity()
                        : BigDecimal.ZERO;
                boolean qtyMatch = exchangeSide.executedQty().subtract(localQty).abs()
                        .compareTo(QTY_TOLERANCE) <= 0;

                ReconciliationStatus status = qtyMatch
                        ? ReconciliationStatus.MATCHED
                        : ReconciliationStatus.DIVERGENT;

                if (status == ReconciliationStatus.MATCHED) matched++;
                else divergent++;

                if (status != ReconciliationStatus.MATCHED || request.includeMatched()) {
                    entries.add(new ReconciliationEntryDto(status, clientOrderId, exchangeSide, localSide));
                }
            }
        }

        // Remaining local transactions have no exchange confirmation
        for (Transaction local : localByClientOrderId.values()) {
            localOnly++;
            entries.add(new ReconciliationEntryDto(
                    ReconciliationStatus.LOCAL_ONLY,
                    local.getClientOrderId(),
                    null,
                    toLocalSide(local)));
        }

        // Exchange-only entries from Binance without our clientOrderId format (manual trades, etc.)
        long unmatchedExchangeCount = exchangeFills.stream()
                .filter(f -> f.clientOrderId() == null || f.clientOrderId().isBlank())
                .count();
        exchangeOnly += (int) unmatchedExchangeCount;

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
                totalQuote, totalFees, feeAsset, firstFill, lastFill);
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
