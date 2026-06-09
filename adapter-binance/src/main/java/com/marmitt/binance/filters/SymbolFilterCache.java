package com.marmitt.binance.filters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.binance.rest.HttpClientPort;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class SymbolFilterCache {

    private static final String EXCHANGE_INFO_PATH = "/api/v3/exchangeInfo";

    private final String restBaseUrl;
    private final HttpClientPort httpClient;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, SymbolFilters> cache = new ConcurrentHashMap<>();

    public SymbolFilterCache(String restBaseUrl, HttpClientPort httpClient, ObjectMapper objectMapper) {
        this.restBaseUrl = restBaseUrl;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public SymbolFilters getFilters(String symbol) {
        return cache.computeIfAbsent(symbol, this::loadFilters);
    }

    public void loadAndCache(String symbol) {
        cache.put(symbol, loadFilters(symbol));
    }

    public void invalidateAll() {
        cache.clear();
        log.info("Symbol filter cache invalidated");
    }

    private SymbolFilters loadFilters(String symbol) {
        try {
            String url = restBaseUrl + EXCHANGE_INFO_PATH + "?symbol=" + symbol;
            HttpClientPort.HttpResponse response = httpClient.get(url, Map.of());
            if (!response.isSuccessful()) {
                throw new SymbolFilterLoadException(symbol,
                        "exchangeInfo returned HTTP " + response.statusCode());
            }
            return parseFilters(symbol, response.body());
        } catch (IOException e) {
            throw new SymbolFilterLoadException(symbol, "HTTP call failed: " + e.getMessage(), e);
        }
    }

    private SymbolFilters parseFilters(String symbol, String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            for (JsonNode symbolNode : root.path("symbols")) {
                if (symbol.equals(symbolNode.path("symbol").asText())) {
                    return parseSymbolFilters(symbol, symbolNode.path("filters"));
                }
            }
            throw new SymbolFilterLoadException(symbol, "symbol not found in exchangeInfo response");
        } catch (SymbolFilterLoadException e) {
            throw e;
        } catch (Exception e) {
            throw new SymbolFilterLoadException(symbol, "failed to parse exchangeInfo: " + e.getMessage(), e);
        }
    }

    private SymbolFilters parseSymbolFilters(String symbol, JsonNode filters) {
        BigDecimal stepSize = null, minQty = null, maxQty = null;
        BigDecimal tickSize = null;
        BigDecimal minNotional = null;

        for (JsonNode filter : filters) {
            switch (filter.path("filterType").asText()) {
                case "LOT_SIZE" -> {
                    stepSize = decimal(filter, "stepSize");
                    minQty = decimal(filter, "minQty");
                    maxQty = decimal(filter, "maxQty");
                }
                case "PRICE_FILTER" -> tickSize = decimal(filter, "tickSize");
                case "MIN_NOTIONAL", "NOTIONAL" -> {
                    if (minNotional == null) minNotional = decimal(filter, "minNotional");
                }
            }
        }

        if (stepSize == null || minQty == null || maxQty == null) {
            throw new SymbolFilterLoadException(symbol, "LOT_SIZE filter missing from exchangeInfo");
        }
        if (tickSize == null) {
            throw new SymbolFilterLoadException(symbol, "PRICE_FILTER filter missing from exchangeInfo");
        }
        if (minNotional == null) {
            throw new SymbolFilterLoadException(symbol, "MIN_NOTIONAL/NOTIONAL filter missing from exchangeInfo");
        }

        log.info("Loaded filters for {}: stepSize={}, minQty={}, maxQty={}, tickSize={}, minNotional={}",
                symbol, stepSize, minQty, maxQty, tickSize, minNotional);
        return new SymbolFilters(stepSize, minQty, maxQty, tickSize, minNotional);
    }

    private BigDecimal decimal(JsonNode node, String field) {
        return new BigDecimal(node.path(field).asText("0"));
    }
}
