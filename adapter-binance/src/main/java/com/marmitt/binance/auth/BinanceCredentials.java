package com.marmitt.binance.auth;

import java.util.Objects;

public class BinanceCredentials {

    private final String apiKey;
    private final String apiSecret;

    public BinanceCredentials(String apiKey, String apiSecret) {
        Objects.requireNonNull(apiKey, "apiKey cannot be null");
        Objects.requireNonNull(apiSecret, "apiSecret cannot be null");
        if (apiKey.isBlank()) throw new IllegalArgumentException("apiKey cannot be blank");
        if (apiSecret.isBlank()) throw new IllegalArgumentException("apiSecret cannot be blank");
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    @Override
    public String toString() {
        return "BinanceCredentials{apiKey=[REDACTED], apiSecret=[REDACTED]}";
    }
}
