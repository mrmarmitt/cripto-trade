package com.marmitt.core.dto.portfolio;

import com.marmitt.core.enums.PortfolioReservationTtlStatus;

import java.util.List;
import java.util.UUID;

public record PortfolioReservationTtlResult(
        UUID portfolioId,
        String exchangeId,
        PortfolioReservationTtlStatus status,
        String code,
        String message,
        long ttlMs,
        int scannedPendingCount,
        int eligibleNoExchangeOrderIdCount,
        int expiredCount,
        int freshCount,
        int errorCount,
        List<UUID> samples
) {
    public static PortfolioReservationTtlResult clean(UUID portfolioId,
                                                      String exchangeId,
                                                      long ttlMs,
                                                      int scannedPendingCount,
                                                      int eligibleNoExchangeOrderIdCount,
                                                      int freshCount) {
        return new PortfolioReservationTtlResult(
                portfolioId,
                exchangeId,
                PortfolioReservationTtlStatus.CLEAN,
                "CLEAN",
                "No reservation exceeded TTL.",
                ttlMs,
                scannedPendingCount,
                eligibleNoExchangeOrderIdCount,
                0,
                freshCount,
                0,
                List.of()
        );
    }

    public static PortfolioReservationTtlResult expired(UUID portfolioId,
                                                        String exchangeId,
                                                        long ttlMs,
                                                        int scannedPendingCount,
                                                        int eligibleNoExchangeOrderIdCount,
                                                        int expiredCount,
                                                        int freshCount,
                                                        List<UUID> samples) {
        return new PortfolioReservationTtlResult(
                portfolioId,
                exchangeId,
                PortfolioReservationTtlStatus.EXPIRED,
                "TTL_EXPIRED",
                "Expired pending reservations without exchangeOrderId.",
                ttlMs,
                scannedPendingCount,
                eligibleNoExchangeOrderIdCount,
                expiredCount,
                freshCount,
                0,
                samples != null ? List.copyOf(samples) : List.of()
        );
    }

    public static PortfolioReservationTtlResult skipped(UUID portfolioId,
                                                        String exchangeId,
                                                        long ttlMs,
                                                        String code,
                                                        String message) {
        return new PortfolioReservationTtlResult(
                portfolioId,
                exchangeId,
                PortfolioReservationTtlStatus.SKIPPED,
                code,
                message,
                ttlMs,
                0,
                0,
                0,
                0,
                0,
                List.of()
        );
    }

    public static PortfolioReservationTtlResult failed(UUID portfolioId,
                                                       String exchangeId,
                                                       long ttlMs,
                                                       String code,
                                                       String message,
                                                       int scannedPendingCount,
                                                       int eligibleNoExchangeOrderIdCount,
                                                       int expiredCount,
                                                       int freshCount,
                                                       int errorCount,
                                                       List<UUID> samples) {
        return new PortfolioReservationTtlResult(
                portfolioId,
                exchangeId,
                PortfolioReservationTtlStatus.FAILED,
                code,
                message,
                ttlMs,
                scannedPendingCount,
                eligibleNoExchangeOrderIdCount,
                expiredCount,
                freshCount,
                errorCount,
                samples != null ? List.copyOf(samples) : List.of()
        );
    }
}
