package com.marmitt.core.domain.strategy;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

/**
 * Onde uma estrategia remota mora e como o ctrade se identifica para ela (T54): URL base do contrato
 * (ex.: {@code http://host:8090/v1}) e token proprio da estrategia. O core so guarda e repassa; o
 * transporte (cabecalhos, HTTP) e do adapter.
 *
 * <p>{@link #toString()} nunca expoe o token.
 */
public record StrategyEndpoint(String baseUrl, String token) {

    public StrategyEndpoint {
        Objects.requireNonNull(baseUrl, "baseUrl cannot be null");
        baseUrl = baseUrl.strip();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        requireHttpUrl(baseUrl);
        if (token != null && token.isBlank()) {
            token = null;
        }
    }

    public boolean hasToken() {
        return token != null;
    }

    @Override
    public String toString() {
        return "StrategyEndpoint[baseUrl=" + baseUrl + ", token=" + (hasToken() ? "***" : "none") + "]";
    }

    private static void requireHttpUrl(String baseUrl) {
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("baseUrl is not a valid URL: " + baseUrl);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
            throw new IllegalArgumentException("baseUrl must be an absolute http(s) URL: " + baseUrl);
        }
    }
}
