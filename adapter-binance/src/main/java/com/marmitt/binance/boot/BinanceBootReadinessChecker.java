package com.marmitt.binance.boot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.binance.rest.BinanceRestRequestBuilder;
import com.marmitt.binance.rest.RestRequest;
import com.marmitt.core.dto.exchange.boot.ExchangeBootReadiness;
import com.marmitt.core.ports.outbound.http.HttpClientPort;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;

@Slf4j
public class BinanceBootReadinessChecker {

    private static final String PING_PATH = "/api/v3/ping";

    private final String restBaseUrl;
    private final BinanceRestRequestBuilder requestBuilder;
    private final HttpClientPort httpClient;
    private final ObjectMapper objectMapper;

    public BinanceBootReadinessChecker(String restBaseUrl,
                                       BinanceRestRequestBuilder requestBuilder,
                                       HttpClientPort httpClient,
                                       ObjectMapper objectMapper) {
        this.restBaseUrl = restBaseUrl;
        this.requestBuilder = requestBuilder;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public ExchangeBootReadiness check() {
        ExchangeBootReadiness connectivity = checkConnectivity();
        if (!connectivity.ready()) {
            log.info("Binance boot readiness: {} - {}", connectivity.code(), connectivity.message());
            return connectivity;
        }

        ExchangeBootReadiness apiKey = checkApiKey();
        log.info("Binance boot readiness: {} - {}", apiKey.code(), apiKey.message());
        return apiKey;
    }

    private ExchangeBootReadiness checkConnectivity() {
        try {
            HttpClientPort.HttpResponse response = httpClient.get(restBaseUrl + PING_PATH, Map.of());
            if (response.isSuccessful()) {
                return ExchangeBootReadiness.ready("BINANCE", "Connectivity verified.");
            }
            return ExchangeBootReadiness.notReady("BINANCE", "CONNECTIVITY_FAILURE",
                    "Ping returned HTTP " + response.statusCode());
        } catch (IOException e) {
            log.warn("Binance connectivity check failed: {}", e.getMessage());
            return ExchangeBootReadiness.notReady("BINANCE", "CONNECTIVITY_FAILURE",
                    "Ping failed: " + e.getMessage());
        }
    }

    private ExchangeBootReadiness checkApiKey() {
        try {
            RestRequest req = requestBuilder.buildAccountSnapshot();
            HttpClientPort.HttpResponse response = httpClient.get(req.url(), req.headers());
            if (response.isSuccessful()) {
                return checkCanTrade(response.body());
            }
            return switch (response.statusCode()) {
                case 401 -> ExchangeBootReadiness.notReady("BINANCE", "INVALID_API_KEY",
                        "API key rejected by Binance (HTTP 401)");
                case 403 -> ExchangeBootReadiness.notReady("BINANCE", "INSUFFICIENT_PERMISSIONS",
                        "API key lacks trading permissions (HTTP 403)");
                default -> ExchangeBootReadiness.notReady("BINANCE", "UNKNOWN_ERROR",
                        "Account check returned HTTP " + response.statusCode() + ": " + response.body());
            };
        } catch (IOException e) {
            log.warn("Binance account check failed: {}", e.getMessage());
            return ExchangeBootReadiness.notReady("BINANCE", "CONNECTIVITY_FAILURE",
                    "Account check failed: " + e.getMessage());
        }
    }

    private ExchangeBootReadiness checkCanTrade(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode canTrade = node.path("canTrade");
            if (!canTrade.isMissingNode() && !canTrade.asBoolean(true)) {
                return ExchangeBootReadiness.notReady("BINANCE", "INSUFFICIENT_PERMISSIONS",
                        "API key cannot trade (canTrade=false)");
            }
        } catch (Exception e) {
            log.warn("Could not parse canTrade from account response: {}", e.getMessage());
        }
        return ExchangeBootReadiness.ready("BINANCE", "Connectivity and API key verified.");
    }
}
