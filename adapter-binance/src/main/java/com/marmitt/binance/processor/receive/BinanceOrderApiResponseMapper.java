package com.marmitt.binance.processor.receive;

import com.fasterxml.jackson.databind.JsonNode;
import com.marmitt.core.domain.Symbol;
import com.marmitt.core.dto.websocket.data.OrderDataDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

class BinanceOrderApiResponseMapper {

    private BinanceOrderApiResponseMapper() {}

    static OrderDataDto fromResult(JsonNode result) {
        BigDecimal executedQty       = new BigDecimal(result.path("executedQty").asText("0"));
        BigDecimal cumulativeQuoteQty = new BigDecimal(result.path("cummulativeQuoteQty").asText("0"));
        long transactTime = result.path("transactTime").asLong(0);

        return new OrderDataDto(
                String.valueOf(result.path("orderId").asLong()),
                result.path("clientOrderId").asText(),
                Symbol.of(result.path("symbol").asText()),
                mapSide(result.path("side").asText()),
                mapType(result.path("type").asText()),
                new BigDecimal(result.path("origQty").asText("0")),
                executedQty,
                new BigDecimal(result.path("price").asText("0")),
                computeWap(executedQty, cumulativeQuoteQty),
                BigDecimal.ZERO,
                mapStatus(result.path("status").asText()),
                null,
                transactTime > 0 ? Instant.ofEpochMilli(transactTime) : Instant.now()
        );
    }

    static OrderDataDto fromError(JsonNode root) {
        String rejectReason = root.path("error").path("msg").asText("Order rejected by Binance");
        return new OrderDataDto(
                null,
                root.path("id").asText(),
                Symbol.of("UNKNOWN"),
                OrderDataDto.OrderSide.BUY,
                OrderDataDto.OrderType.LIMIT,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                OrderDataDto.OrderStatus.REJECTED,
                rejectReason,
                Instant.now()
        );
    }

    private static BigDecimal computeWap(BigDecimal executedQty, BigDecimal cumulativeQuoteQty) {
        if (executedQty.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return cumulativeQuoteQty.divide(executedQty, 8, RoundingMode.HALF_UP);
    }

    private static OrderDataDto.OrderSide mapSide(String side) {
        return "SELL".equals(side) ? OrderDataDto.OrderSide.SELL : OrderDataDto.OrderSide.BUY;
    }

    private static OrderDataDto.OrderType mapType(String type) {
        return switch (type) {
            case "MARKET" -> OrderDataDto.OrderType.MARKET;
            default -> OrderDataDto.OrderType.LIMIT;
        };
    }

    private static OrderDataDto.OrderStatus mapStatus(String status) {
        return switch (status) {
            case "FILLED" -> OrderDataDto.OrderStatus.FILLED;
            case "PARTIALLY_FILLED" -> OrderDataDto.OrderStatus.PARTIALLY_FILLED;
            case "CANCELED" -> OrderDataDto.OrderStatus.CANCELED;
            case "REJECTED" -> OrderDataDto.OrderStatus.REJECTED;
            case "EXPIRED" -> OrderDataDto.OrderStatus.EXPIRED;
            default -> OrderDataDto.OrderStatus.NEW;
        };
    }
}
