package com.marmitt.binance.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.websocket.data.AccountDataDto;
import com.marmitt.core.exceptions.ExchangeQueryException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class BinanceAccountMapper {

    private final ObjectMapper objectMapper;

    public BinanceAccountMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AccountDataDto fromJson(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            Map<String, BigDecimal> balances = new LinkedHashMap<>();
            Map<String, BigDecimal> locked   = new LinkedHashMap<>();

            JsonNode balancesNode = root.path("balances");
            for (JsonNode b : balancesNode) {
                String asset = b.path("asset").asText();
                BigDecimal free  = decimal(b, "free");
                BigDecimal lockedAmt = decimal(b, "locked");
                if (free.compareTo(BigDecimal.ZERO) > 0 || lockedAmt.compareTo(BigDecimal.ZERO) > 0) {
                    balances.put(asset, free);
                    locked.put(asset, lockedAmt);
                }
            }

            long updateTime = root.path("updateTime").asLong(0);
            Instant lastUpdate = updateTime > 0 ? Instant.ofEpochMilli(updateTime) : Instant.now();

            return new AccountDataDto(null, balances, locked, lastUpdate);
        } catch (Exception e) {
            throw new ExchangeQueryException("BINANCE", ExchangeQueryException.ErrorType.UNKNOWN,
                    "Failed to parse account snapshot: " + e.getMessage(), e);
        }
    }

    private BigDecimal decimal(JsonNode node, String field) {
        String text = node.path(field).asText("0");
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
