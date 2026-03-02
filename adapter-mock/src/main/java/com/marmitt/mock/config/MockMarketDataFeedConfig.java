package com.marmitt.mock.config;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Configuration for the synthetic market-data feed produced by the mock adapter.
 */
public record MockMarketDataFeedConfig(
        boolean enabled,
        long tickIntervalMs,
        int maxDeltaBpsPerTick,
        int spreadBps,
        BigDecimal defaultStartPrice,
        BigDecimal defaultVolume
) {

    public MockMarketDataFeedConfig {
        Objects.requireNonNull(defaultStartPrice, "defaultStartPrice cannot be null");
        Objects.requireNonNull(defaultVolume, "defaultVolume cannot be null");
        if (tickIntervalMs <= 0) {
            throw new IllegalArgumentException("tickIntervalMs must be > 0");
        }
        if (maxDeltaBpsPerTick < 0) {
            throw new IllegalArgumentException("maxDeltaBpsPerTick must be >= 0");
        }
        if (spreadBps < 0) {
            throw new IllegalArgumentException("spreadBps must be >= 0");
        }
        if (defaultStartPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("defaultStartPrice must be > 0");
        }
        if (defaultVolume.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("defaultVolume must be > 0");
        }
    }

    public static MockMarketDataFeedConfig defaultConfig() {
        return new MockMarketDataFeedConfig(
                true,
                1_000L,
                10,
                4,
                new BigDecimal("65000.00000000"),
                new BigDecimal("1000.00000000")
        );
    }
}
