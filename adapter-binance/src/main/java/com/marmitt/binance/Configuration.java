package com.marmitt.binance;

import java.util.Objects;

public class Configuration {

    public static final String SINGLE_STREAM_PATH = "/ws";
    public static final String COMBINED_STREAM_PATH = "/stream";

    private final String webSocketBaseUrl;
    private final String restBaseUrl;

    public Configuration(String webSocketBaseUrl, String restBaseUrl) {
        this.webSocketBaseUrl = requireNonBlank(webSocketBaseUrl, "webSocketBaseUrl");
        this.restBaseUrl = requireNonBlank(restBaseUrl, "restBaseUrl");
    }

    public String getWebSocketBaseUrl() {
        return webSocketBaseUrl;
    }

    public String getRestBaseUrl() {
        return restBaseUrl;
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
}
