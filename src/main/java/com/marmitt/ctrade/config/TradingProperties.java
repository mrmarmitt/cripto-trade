package com.marmitt.ctrade.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "trading")
public record TradingProperties(
        PriceCache priceCache,
        Pair pair) {

    public record PriceCache(
            int ttlMinutes,
            int maxHistorySize,
            int cleanupIntervalMinutes) {

        public Duration getTtlMinutesAsDuration() {
            return Duration.ofMinutes(ttlMinutes);
        }
    }

    public record Pair(
            List<String> active) {
    }
}
