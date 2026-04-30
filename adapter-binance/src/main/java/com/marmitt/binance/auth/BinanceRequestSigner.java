package com.marmitt.binance.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class BinanceRequestSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private final BinanceCredentials credentials;

    public BinanceRequestSigner(BinanceCredentials credentials) {
        this.credentials = Objects.requireNonNull(credentials, "credentials cannot be null");
    }

    /**
     * Signs params for the Binance WebSocket API.
     *
     * Adds apiKey and timestamp to the provided params, builds an alphabetically sorted
     * canonical string, computes HMAC-SHA256, and inserts the resulting signature.
     * Returns a new sorted map — the original map is not mutated.
     */
    public Map<String, Object> signWebSocketParams(Map<String, Object> params) {
        TreeMap<String, Object> signed = new TreeMap<>(params);
        signed.put("apiKey", credentials.getApiKey());
        signed.put("timestamp", System.currentTimeMillis());

        String canonical = buildCanonicalString(signed);
        signed.put("signature", hmacSha256(credentials.getApiSecret(), canonical));

        return signed;
    }

    /**
     * Signs a query string for the Binance REST API.
     *
     * Returns the query string with {@code &signature=<hex>} appended.
     * The caller is responsible for including timestamp in the query string before signing.
     */
    public String signQueryString(String queryString) {
        Objects.requireNonNull(queryString, "queryString cannot be null");
        return queryString + "&signature=" + hmacSha256(credentials.getApiSecret(), queryString);
    }

    public String getApiKey() {
        return credentials.getApiKey();
    }

    private String buildCanonicalString(TreeMap<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> {
            if (!sb.isEmpty()) sb.append('&');
            sb.append(k).append('=').append(v);
        });
        return sb.toString();
    }

    private String hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256 signature", e);
        }
    }
}
