package com.marmitt.binance;

public record BinanceConnectionConfig(
        String apiKey,
        String apiSecret,
        String wsBaseUrl,
        String restBaseUrl
) {}
