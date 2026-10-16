package com.marmitt.binance.processor.send;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.binance.auth.BinanceCredentials;
import com.marmitt.binance.auth.BinanceRequestSigner;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.enums.OrderSide;
import com.marmitt.core.enums.OrderType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * T45: a WS API segue a mesma regra do REST para price/timeInForce ({@code BinanceOrderTypeRules}).
 */
class OrderProcessorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OrderProcessor processor = new OrderProcessor(objectMapper,
            new BinanceRequestSigner(new BinanceCredentials("key", "secret")));

    @Test
    void marketOrderOmitsPriceAndTimeInForceEvenWithReferencePrice() throws Exception {
        JsonNode params = paramsOf(new SendOrderRequest("BINANCE", "BTCUSDT", new BigDecimal("0.00099"),
                new BigDecimal("65000.00"), OrderType.MARKET, OrderSide.SELL, "panic-1"));

        assertEquals("MARKET", params.path("type").asText());
        assertFalse(params.has("price"));
        assertFalse(params.has("timeInForce"));
    }

    @Test
    void limitOrderSendsPriceAndGtc() throws Exception {
        JsonNode params = paramsOf(new SendOrderRequest("BINANCE", "BTCUSDT", new BigDecimal("0.001"),
                new BigDecimal("65000.00"), OrderType.LIMIT, OrderSide.BUY, "limit-1"));

        assertEquals("65000.00", params.path("price").asText());
        assertEquals("GTC", params.path("timeInForce").asText());
    }

    private JsonNode paramsOf(SendOrderRequest request) throws Exception {
        return objectMapper.readTree(processor.execute(request)).path("params");
    }
}
