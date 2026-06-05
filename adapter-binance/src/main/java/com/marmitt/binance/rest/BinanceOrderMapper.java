package com.marmitt.binance.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.domain.Symbol;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.exceptions.ExchangeQueryException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class BinanceOrderMapper {

    private final ObjectMapper objectMapper;

    public BinanceOrderMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    OrderDataDto fromNode(JsonNode node) {
        String exchangeOrderId = node.path("orderId").asText();
        String clientOrderId   = node.path("clientOrderId").asText();
        String symbolRaw       = node.path("symbol").asText();
        String sideRaw         = node.path("side").asText();
        String typeRaw         = node.path("type").asText();
        String statusRaw       = node.path("status").asText();
        BigDecimal quantity          = decimal(node, "origQty");
        BigDecimal executedQty       = decimal(node, "executedQty");
        BigDecimal cumulativeQuoteQty = decimal(node, "cummulativeQuoteQty");
        BigDecimal price             = decimal(node, "price");
        BigDecimal executedPrice     = computeWap(executedQty, cumulativeQuoteQty);
        long timeMs = node.path("time").asLong(0);
        if (timeMs == 0) {
            timeMs = node.path("transactTime").asLong(0);
        }

        return new OrderDataDto(
                exchangeOrderId,
                clientOrderId,
                Symbol.of(symbolRaw),
                mapSide(sideRaw),
                mapType(typeRaw),
                quantity,
                executedQty,
                price,
                executedPrice,
                BigDecimal.ZERO,
                mapStatus(statusRaw),
                null,
                timeMs > 0 ? Instant.ofEpochMilli(timeMs) : Instant.now()
        );
    }

    public OrderDataDto fromJson(String json) {
        try {
            return fromNode(objectMapper.readTree(json));
        } catch (Exception e) {
            throw new ExchangeQueryException("BINANCE", ExchangeQueryException.ErrorType.UNKNOWN,
                    "Failed to parse order response: " + e.getMessage(), e);
        }
    }

    List<OrderDataDto> listFromJson(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            List<OrderDataDto> result = new ArrayList<>();
            for (JsonNode node : root) {
                result.add(fromNode(node));
            }
            return result;
        } catch (Exception e) {
            throw new ExchangeQueryException("BINANCE", ExchangeQueryException.ErrorType.UNKNOWN,
                    "Failed to parse order list response: " + e.getMessage(), e);
        }
    }

    private BigDecimal computeWap(BigDecimal qty, BigDecimal quoteQty) {
        if (qty.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return quoteQty.divide(qty, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal(JsonNode node, String field) {
        String text = node.path(field).asText("0");
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private OrderDataDto.OrderSide mapSide(String raw) {
        return switch (raw.toUpperCase()) {
            case "BUY"  -> OrderDataDto.OrderSide.BUY;
            case "SELL" -> OrderDataDto.OrderSide.SELL;
            default     -> throw new ExchangeQueryException("BINANCE", ExchangeQueryException.ErrorType.UNKNOWN,
                    "Unknown Binance order side: " + raw);
        };
    }

    private OrderDataDto.OrderType mapType(String raw) {
        return switch (raw.toUpperCase()) {
            case "MARKET"                              -> OrderDataDto.OrderType.MARKET;
            case "LIMIT", "LIMIT_MAKER"               -> OrderDataDto.OrderType.LIMIT;
            case "STOP_LOSS", "TAKE_PROFIT"            -> OrderDataDto.OrderType.STOP;
            case "STOP_LOSS_LIMIT", "TAKE_PROFIT_LIMIT" -> OrderDataDto.OrderType.STOP_LIMIT;
            default -> OrderDataDto.OrderType.LIMIT;
        };
    }

    private OrderDataDto.OrderStatus mapStatus(String raw) {
        return switch (raw.toUpperCase()) {
            case "NEW"                        -> OrderDataDto.OrderStatus.NEW;
            case "PARTIALLY_FILLED"           -> OrderDataDto.OrderStatus.PARTIALLY_FILLED;
            case "FILLED"                     -> OrderDataDto.OrderStatus.FILLED;
            case "CANCELED"                   -> OrderDataDto.OrderStatus.CANCELED;
            case "REJECTED"                   -> OrderDataDto.OrderStatus.REJECTED;
            case "EXPIRED", "EXPIRED_IN_MATCH" -> OrderDataDto.OrderStatus.EXPIRED;
            default -> throw new ExchangeQueryException("BINANCE", ExchangeQueryException.ErrorType.UNKNOWN,
                    "Unknown Binance order status: " + raw);
        };
    }
}
