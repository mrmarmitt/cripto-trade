package com.marmitt.mock.config;

import java.math.BigDecimal;
import java.util.Objects;

public record MockScenarioConfig(
        long randomSeed,
        Timing timing,
        Flow flow,
        Duplicates duplicates,
        OutOfOrder outOfOrder,
        Failures failures,
        FeeSettings fees,
        SlippageSettings slippage,
        ValidationSettings validation
) {

    public MockScenarioConfig {
        Objects.requireNonNull(timing, "timing cannot be null");
        Objects.requireNonNull(flow, "flow cannot be null");
        Objects.requireNonNull(duplicates, "duplicates cannot be null");
        Objects.requireNonNull(outOfOrder, "outOfOrder cannot be null");
        Objects.requireNonNull(failures, "failures cannot be null");
        Objects.requireNonNull(fees, "fees cannot be null");
        Objects.requireNonNull(slippage, "slippage cannot be null");
        Objects.requireNonNull(validation, "validation cannot be null");

        if (timing.latencyMinMs() < 0 || timing.latencyMaxMs() < 0 || timing.latencyMaxMs() < timing.latencyMinMs()) {
            throw new IllegalArgumentException("Invalid latency range");
        }
        if (flow.partialFillCount() < 0) {
            throw new IllegalArgumentException("partialFillCount cannot be negative");
        }
        if (duplicates.maxDuplicatesPerEvent() < 0) {
            throw new IllegalArgumentException("maxDuplicatesPerEvent cannot be negative");
        }
        validateRatio(duplicates.ratio(), "duplicateRatio");
        validateRatio(outOfOrder.ratio(), "outOfOrderRatio");
        validateRatio(failures.cancelRatio(), "cancelRatio");
        validateRatio(failures.expireRatio(), "expireRatio");
        validateFeeSettings(fees);
        validateSlippageSettings(slippage);
        validateValidationSettings(validation);
    }

    private static void validateRatio(double value, String label) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(label + " must be between 0 and 1");
        }
    }

    private static void validateFeeSettings(FeeSettings feeSettings) {
        Objects.requireNonNull(feeSettings.mode(), "feeSettings.mode cannot be null");
        Objects.requireNonNull(feeSettings.feeRate(), "feeSettings.feeRate cannot be null");
        if (feeSettings.feeRate().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("feeRate cannot be negative");
        }
        if (feeSettings.feeRate().compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("feeRate cannot be greater than 1");
        }
    }

    private static void validateSlippageSettings(SlippageSettings settings) {
        Objects.requireNonNull(settings.mode(), "slippage.mode cannot be null");
        if (settings.maxSlippageBps() < 0) {
            throw new IllegalArgumentException("maxSlippageBps cannot be negative");
        }
        if (settings.priceImprovementChance() < 0.0 || settings.priceImprovementChance() > 1.0) {
            throw new IllegalArgumentException("priceImprovementChance must be between 0 and 1");
        }
        if (settings.priceStepBps() < 0) {
            throw new IllegalArgumentException("priceStepBps cannot be negative");
        }
        if (settings.priceStepBps() > settings.maxSlippageBps()) {
            throw new IllegalArgumentException("priceStepBps cannot exceed maxSlippageBps");
        }
    }

    private static void validateValidationSettings(ValidationSettings settings) {
        Objects.requireNonNull(settings.minQty(), "validation.minQty cannot be null");
        Objects.requireNonNull(settings.stepSize(), "validation.stepSize cannot be null");
        Objects.requireNonNull(settings.minNotional(), "validation.minNotional cannot be null");
        if (settings.minQty().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("minQty cannot be negative");
        }
        if (settings.stepSize().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("stepSize cannot be negative");
        }
        if (settings.minNotional().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("minNotional cannot be negative");
        }
    }

    public static MockScenarioConfig defaultConfig() {
        return new MockScenarioConfig(
                42L,
                new Timing(100L, 400L),
                new Flow(2, null),
                new Duplicates(0.10, 1),
                new OutOfOrder(0.05),
                new Failures(0.02, 0.01),
                new FeeSettings(FeeMode.SAME_CURRENCY, new BigDecimal("0.0010")),
                new SlippageSettings(SlippageMode.MARKET_ONLY, 15, 0.10, 2),
                new ValidationSettings(new BigDecimal("0.000001"), new BigDecimal("0.000001"), new BigDecimal("1.0"))
        );
    }

    public record Timing(long latencyMinMs, long latencyMaxMs) {}

    public record Flow(int partialFillCount, java.util.List<java.math.BigDecimal> partialFillFractions) {}

    public record Duplicates(double ratio, int maxDuplicatesPerEvent) {}

    public record OutOfOrder(double ratio) {}

    public record Failures(double cancelRatio, double expireRatio) {}

    public enum FeeMode {
        NONE,
        SAME_CURRENCY
    }

    public record FeeSettings(FeeMode mode, BigDecimal feeRate) {}

    public enum SlippageMode {
        NONE,
        MARKET_ONLY,
        ALL_ORDERS
    }

    /**
     * maxSlippageBps: limite absoluto de slippage em basis points (1 bps = 0.01%).
     * priceImprovementChance: chance de executar com melhoria de preco.
     * priceStepBps: granularidade de slippage para simular book discreto.
     */
    public record SlippageSettings(SlippageMode mode,
                                   int maxSlippageBps,
                                   double priceImprovementChance,
                                   int priceStepBps) {}

    /**
     * minQty: quantidade mínima (base asset).
     * stepSize: incremento mínimo para quantidade.
     * minNotional: valor mínimo da ordem (quote asset).
     */
    public record ValidationSettings(BigDecimal minQty,
                                     BigDecimal stepSize,
                                     BigDecimal minNotional) {}
}
