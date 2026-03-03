package com.marmitt.core.dto.portfolio;

import com.marmitt.core.enums.PortfolioZombieDetectionStatus;

import java.util.List;
import java.util.UUID;

public record PortfolioZombieDetectionResult(
        UUID portfolioId,
        String exchangeId,
        PortfolioZombieDetectionStatus status,
        String code,
        String message,
        int openOrders,
        int zombieCount,
        int invalidFormatCount,
        int unknownRunnerCount,
        int noLocalMatchCount,
        int unknownSymbolCount,
        List<PortfolioZombieCandidate> samples
) {
    public static PortfolioZombieDetectionResult clean(UUID portfolioId,
                                                       String exchangeId,
                                                       int openOrders) {
        return new PortfolioZombieDetectionResult(
                portfolioId,
                exchangeId,
                PortfolioZombieDetectionStatus.CLEAN,
                "CLEAN",
                "No exchange zombie orders detected.",
                openOrders,
                0,
                0,
                0,
                0,
                0,
                List.of()
        );
    }

    public static PortfolioZombieDetectionResult detected(UUID portfolioId,
                                                          String exchangeId,
                                                          int openOrders,
                                                          int invalidFormatCount,
                                                          int unknownRunnerCount,
                                                          int noLocalMatchCount,
                                                          int unknownSymbolCount,
                                                          List<PortfolioZombieCandidate> samples) {
        int zombieCount = invalidFormatCount + unknownRunnerCount + noLocalMatchCount + unknownSymbolCount;

        return new PortfolioZombieDetectionResult(
                portfolioId,
                exchangeId,
                PortfolioZombieDetectionStatus.DETECTED,
                "ZOMBIE_DETECTED",
                "Open orders from exchange were classified as zombie candidates.",
                openOrders,
                zombieCount,
                invalidFormatCount,
                unknownRunnerCount,
                noLocalMatchCount,
                unknownSymbolCount,
                samples != null ? List.copyOf(samples) : List.of()
        );
    }

    public static PortfolioZombieDetectionResult skipped(UUID portfolioId,
                                                         String exchangeId,
                                                         String code,
                                                         String message) {
        return new PortfolioZombieDetectionResult(
                portfolioId,
                exchangeId,
                PortfolioZombieDetectionStatus.SKIPPED,
                code,
                message,
                0,
                0,
                0,
                0,
                0,
                0,
                List.of()
        );
    }

    public static PortfolioZombieDetectionResult failed(UUID portfolioId,
                                                        String exchangeId,
                                                        String code,
                                                        String message) {
        return new PortfolioZombieDetectionResult(
                portfolioId,
                exchangeId,
                PortfolioZombieDetectionStatus.FAILED,
                code,
                message,
                0,
                0,
                0,
                0,
                0,
                0,
                List.of()
        );
    }
}
