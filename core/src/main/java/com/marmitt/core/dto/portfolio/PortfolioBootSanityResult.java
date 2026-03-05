package com.marmitt.core.dto.portfolio;

import com.marmitt.core.enums.PortfolioSanityStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record PortfolioBootSanityResult(
        UUID portfolioId,
        String exchangeId,
        PortfolioSanityStatus status,
        String code,
        String message,
        String baseCurrency,
        BigDecimal localTotal,
        BigDecimal exchangeTotal,
        BigDecimal signedDelta,
        BigDecimal absoluteDeviation
) {
    public static PortfolioBootSanityResult pass(UUID portfolioId,
                                                 String exchangeId,
                                                 String baseCurrency,
                                                 BigDecimal localTotal,
                                                 BigDecimal exchangeTotal,
                                                 BigDecimal signedDelta,
                                                 BigDecimal absoluteDeviation) {
        return new PortfolioBootSanityResult(
                portfolioId,
                exchangeId,
                PortfolioSanityStatus.PASS,
                "PASS",
                "Portfolio sanity check passed within threshold.",
                baseCurrency,
                localTotal,
                exchangeTotal,
                signedDelta,
                absoluteDeviation
        );
    }

    public static PortfolioBootSanityResult warnSurplus(UUID portfolioId,
                                                        String exchangeId,
                                                        String baseCurrency,
                                                        BigDecimal localTotal,
                                                        BigDecimal exchangeTotal,
                                                        BigDecimal signedDelta,
                                                        BigDecimal absoluteDeviation) {
        return new PortfolioBootSanityResult(
                portfolioId,
                exchangeId,
                PortfolioSanityStatus.WARN_SURPLUS,
                "WARN_SURPLUS",
                "Exchange base balance is greater than local total. Continue and reconcile in runner phase.",
                baseCurrency,
                localTotal,
                exchangeTotal,
                signedDelta,
                absoluteDeviation
        );
    }

    public static PortfolioBootSanityResult failDeficit(UUID portfolioId,
                                                        String exchangeId,
                                                        String baseCurrency,
                                                        BigDecimal localTotal,
                                                        BigDecimal exchangeTotal,
                                                        BigDecimal signedDelta,
                                                        BigDecimal absoluteDeviation) {
        return new PortfolioBootSanityResult(
                portfolioId,
                exchangeId,
                PortfolioSanityStatus.FAIL_DEFICIT,
                "FAIL_DEFICIT",
                "Exchange base balance is lower than local total. Critical deficit detected.",
                baseCurrency,
                localTotal,
                exchangeTotal,
                signedDelta,
                absoluteDeviation
        );
    }

    public static PortfolioBootSanityResult skipped(UUID portfolioId,
                                                    String exchangeId,
                                                    String code,
                                                    String message) {
        return new PortfolioBootSanityResult(
                portfolioId,
                exchangeId,
                PortfolioSanityStatus.SKIPPED,
                code,
                message,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static PortfolioBootSanityResult failed(UUID portfolioId,
                                                   String exchangeId,
                                                   String code,
                                                   String message) {
        return new PortfolioBootSanityResult(
                portfolioId,
                exchangeId,
                PortfolioSanityStatus.FAILED,
                code,
                message,
                null,
                null,
                null,
                null,
                null
        );
    }
}
