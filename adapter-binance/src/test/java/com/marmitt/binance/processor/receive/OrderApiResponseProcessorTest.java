package com.marmitt.binance.processor.receive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderApiResponseProcessorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final OrderApiResponseProcessor processor = new OrderApiResponseProcessor();

    private MessageContext ctx() {
        return MessageContext.createUserData("BINANCE", UUID.randomUUID());
    }

    @Test
    void success200_mapsToNewOrderDataDto() throws Exception {
        String json = """
                {
                  "id": "v1rcd1t1000000000000s001B_abc123def456",
                  "status": 200,
                  "result": {
                    "symbol": "BTCUSDT",
                    "orderId": 9876543,
                    "clientOrderId": "v1rcd1t1000000000000s001B_abc123def456",
                    "side": "BUY",
                    "type": "LIMIT",
                    "origQty": "0.00100000",
                    "executedQty": "0.00000000",
                    "cummulativeQuoteQty": "0.00000000",
                    "price": "95000.00000000",
                    "status": "NEW",
                    "transactTime": 1700000000000
                  }
                }
                """;
        JsonNode root = mapper.readTree(json);

        ProcessingResult<?> pr = processor.process(root, root.path("result"), 200, "c1", json, ctx());

        assertTrue(pr.isSuccess());
        OrderDataDto dto = (OrderDataDto) pr.getData().orElseThrow();
        assertEquals("v1rcd1t1000000000000s001B_abc123def456", dto.clientOrderId());
        assertEquals("9876543", dto.orderId());
        assertEquals("BTCUSDT", dto.symbol().value());
        assertEquals(OrderDataDto.OrderSide.BUY, dto.side());
        assertEquals(OrderDataDto.OrderStatus.NEW, dto.status());
        assertNull(dto.rejectReason());
    }

    @Test
    void error400_mapsToRejectedOrderDataDto() throws Exception {
        String json = """
                {
                  "id": "v1rcd1t1000000000000s001B_abc123def456",
                  "status": 400,
                  "error": {
                    "code": -2010,
                    "msg": "Account has insufficient balance for requested action."
                  }
                }
                """;
        JsonNode root = mapper.readTree(json);

        ProcessingResult<?> pr = processor.process(root, root.path("result"), 400, "c2", json, ctx());

        assertTrue(pr.isSuccess());
        OrderDataDto dto = (OrderDataDto) pr.getData().orElseThrow();
        assertEquals("v1rcd1t1000000000000s001B_abc123def456", dto.clientOrderId());
        assertEquals(OrderDataDto.OrderStatus.REJECTED, dto.status());
        assertTrue(dto.rejectReason().contains("insufficient balance"));
    }

    @Test
    void success200_noSymbol_returnsIgnored() throws Exception {
        String json = """
                {"id": "some-uuid", "status": 200, "result": {}}
                """;
        JsonNode root = mapper.readTree(json);

        ProcessingResult<?> pr = processor.process(root, root.path("result"), 200, "c3", json, ctx());

        assertFalse(pr.isSuccess());
        assertFalse(pr.isError());
    }

    @Test
    void immediateFill_200_filledStatus_returnsIgnored() throws Exception {
        String json = """
                {
                  "id": "v1rcd1t1000000000000s001B_abc123def456",
                  "status": 200,
                  "result": {
                    "symbol": "BTCUSDT",
                    "orderId": 9876543,
                    "clientOrderId": "v1rcd1t1000000000000s001B_abc123def456",
                    "side": "BUY",
                    "type": "LIMIT",
                    "origQty": "0.001",
                    "executedQty": "0.001",
                    "cummulativeQuoteQty": "95.00000000",
                    "price": "95000",
                    "status": "FILLED",
                    "transactTime": 1700000000000,
                    "fills": [{"price": "95000", "qty": "0.001", "commission": "0.00000001", "commissionAsset": "BTC"}]
                  }
                }
                """;
        JsonNode root = mapper.readTree(json);

        ProcessingResult<?> pr = processor.process(root, root.path("result"), 200, "c-fill", json, ctx());

        assertFalse(pr.isSuccess(), "immediate FILLED must be ignored to defer to fee-accurate executionReport");
        assertFalse(pr.isError(), "ignored result must not be an error");
    }

    @Test
    void error5xx_returnsErrorResult_notRejected() throws Exception {
        String json = """
                {
                  "id": "v1rcd1t1000000000000s001B_abc123def456",
                  "status": 504,
                  "error": {
                    "code": -1001,
                    "msg": "An unknown error occurred while processing the request."
                  }
                }
                """;
        JsonNode root = mapper.readTree(json);

        ProcessingResult<?> pr = processor.process(root, root.path("result"), 504, "c5", json, ctx());

        assertFalse(pr.isSuccess(), "5xx must not be treated as success/REJECTED");
        assertTrue(pr.isError(), "5xx must return error so transaction stays PENDING for recovery");
    }

    @Test
    void sellSide_mappedCorrectly() throws Exception {
        String json = """
                {
                  "id": "v1rcd1t1000000000000s001S_abc123def456",
                  "status": 200,
                  "result": {
                    "symbol": "ETHUSDT",
                    "orderId": 111,
                    "clientOrderId": "v1rcd1t1000000000000s001S_abc123def456",
                    "side": "SELL",
                    "type": "LIMIT",
                    "origQty": "0.01000000",
                    "executedQty": "0.00000000",
                    "cummulativeQuoteQty": "0.00000000",
                    "price": "3000.00000000",
                    "status": "NEW",
                    "transactTime": 1700000000000
                  }
                }
                """;
        JsonNode root = mapper.readTree(json);

        ProcessingResult<?> pr = processor.process(root, root.path("result"), 200, "c4", json, ctx());

        assertTrue(pr.isSuccess());
        OrderDataDto dto = (OrderDataDto) pr.getData().orElseThrow();
        assertEquals(OrderDataDto.OrderSide.SELL, dto.side());
    }
}
