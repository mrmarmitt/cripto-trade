package com.marmitt.binance.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BinanceOrderMapperTest {

    private final BinanceOrderMapper mapper = new BinanceOrderMapper(new ObjectMapper());

    @Test
    void fromJson_shouldMapAllFields() {
        String json = """
                {
                  "orderId": 42,
                  "clientOrderId": "my-order",
                  "symbol": "BTCUSDT",
                  "side": "BUY",
                  "type": "LIMIT",
                  "status": "FILLED",
                  "origQty": "0.001",
                  "executedQty": "0.001",
                  "price": "95000.00000000",
                  "cummulativeQuoteQty": "95.00000000",
                  "time": 1700000000000
                }
                """;

        OrderDataDto dto = mapper.fromJson(json);

        assertEquals("42", dto.orderId());
        assertEquals("my-order", dto.clientOrderId());
        assertEquals("BTCUSDT", dto.symbol().value());
        assertEquals(OrderDataDto.OrderSide.BUY, dto.side());
        assertEquals(OrderDataDto.OrderType.LIMIT, dto.type());
        assertEquals(OrderDataDto.OrderStatus.FILLED, dto.status());
        assertEquals(new BigDecimal("0.001"), dto.executedQuantity());
        assertEquals(new BigDecimal("95000.00000000"), dto.price());
        // WAP = cummulativeQuoteQty / executedQty = 95.00 / 0.001 = 95000
        assertEquals(0, dto.executedPrice().compareTo(new BigDecimal("95000.00000000")));
        assertNotNull(dto.timestamp());
    }

    @Test
    void fromJson_shouldMapPartiallyFilled() {
        String json = """
                {
                  "orderId": 10,
                  "clientOrderId": "p-order",
                  "symbol": "ETHUSDT",
                  "side": "SELL",
                  "type": "MARKET",
                  "status": "PARTIALLY_FILLED",
                  "origQty": "1.0",
                  "executedQty": "0.5",
                  "price": "0",
                  "cummulativeQuoteQty": "1500.00",
                  "time": 1700000000000
                }
                """;

        OrderDataDto dto = mapper.fromJson(json);

        assertEquals(OrderDataDto.OrderStatus.PARTIALLY_FILLED, dto.status());
        assertEquals(OrderDataDto.OrderType.MARKET, dto.type());
        // WAP = 1500.00 / 0.5 = 3000
        assertEquals(0, dto.executedPrice().compareTo(new BigDecimal("3000.00000000")));
    }

    @Test
    void fromJson_shouldReturnZeroWap_whenExecutedQtyIsZero() {
        String json = """
                {
                  "orderId": 1,
                  "clientOrderId": "new-order",
                  "symbol": "BTCUSDT",
                  "side": "BUY",
                  "type": "LIMIT",
                  "status": "NEW",
                  "origQty": "0.001",
                  "executedQty": "0",
                  "price": "90000",
                  "cummulativeQuoteQty": "0",
                  "time": 1700000000000
                }
                """;

        OrderDataDto dto = mapper.fromJson(json);

        assertEquals(OrderDataDto.OrderStatus.NEW, dto.status());
        assertEquals(BigDecimal.ZERO, dto.executedPrice());
    }

    @Test
    void fromJson_shouldMapExpiredInMatchAsExpired() {
        String json = """
                {
                  "orderId": 99,
                  "clientOrderId": "stp-order",
                  "symbol": "BTCUSDT",
                  "side": "BUY",
                  "type": "LIMIT",
                  "status": "EXPIRED_IN_MATCH",
                  "origQty": "0.001",
                  "executedQty": "0",
                  "price": "90000",
                  "cummulativeQuoteQty": "0",
                  "time": 1700000000000
                }
                """;

        assertEquals(OrderDataDto.OrderStatus.EXPIRED, mapper.fromJson(json).status());
    }

    @Test
    void listFromJson_shouldReturnEmptyList_forEmptyArray() {
        List<OrderDataDto> result = mapper.listFromJson("[]");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void listFromJson_shouldMapMultipleOrders() {
        String json = """
                [
                  {
                    "orderId": 1, "clientOrderId": "a", "symbol": "BTCUSDT",
                    "side": "BUY", "type": "LIMIT", "status": "NEW",
                    "origQty": "0.001", "executedQty": "0",
                    "price": "90000", "cummulativeQuoteQty": "0", "time": 1700000000000
                  },
                  {
                    "orderId": 2, "clientOrderId": "b", "symbol": "ETHUSDT",
                    "side": "SELL", "type": "MARKET", "status": "FILLED",
                    "origQty": "1.0", "executedQty": "1.0",
                    "price": "0", "cummulativeQuoteQty": "3000", "time": 1700000000001
                  }
                ]
                """;

        List<OrderDataDto> result = mapper.listFromJson(json);

        assertEquals(2, result.size());
        assertEquals("a", result.get(0).clientOrderId());
        assertEquals("b", result.get(1).clientOrderId());
        assertEquals(OrderDataDto.OrderStatus.FILLED, result.get(1).status());
    }
}
