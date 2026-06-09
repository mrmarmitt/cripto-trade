package com.marmitt.binance.processor.receive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BinanceUserDataProcessorRoutingTest {

    private final List<Object> publishedEvents = new ArrayList<>();
    private BinanceUserDataProcessor processor;

    @BeforeEach
    void setup() {
        EventPublisherPort publisher = new EventPublisherPort() {
            @Override public void publishEvent(Object event) { publishedEvents.add(event); }
            @Override public void publishEventSync(Object event) { publishedEvents.add(event); }
        };
        processor = new BinanceUserDataProcessor(new ObjectMapper(), publisher);
    }

    private MessageContext ctx() {
        return MessageContext.createUserData("BINANCE", UUID.randomUUID());
    }

    @Test
    void orderSuccess_routesToOrderProcessor() {
        String msg = """
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
                    "executedQty": "0",
                    "cummulativeQuoteQty": "0",
                    "price": "95000",
                    "status": "NEW",
                    "transactTime": 1700000000000
                  }
                }
                """;

        ProcessingResult<?> result = processor.processMessage(msg, ctx());

        assertTrue(result.isSuccess());
        assertInstanceOf(OrderDataDto.class, result.getData().orElseThrow());
        assertEquals(OrderDataDto.OrderStatus.NEW,
                ((OrderDataDto) result.getData().orElseThrow()).status());
        assertTrue(publishedEvents.isEmpty(), "no WebSocketFailedEvent should be published on order success");
    }

    @Test
    void orderRejection_routesToOrderProcessorAsRejected() {
        String msg = """
                {
                  "id": "v1rcd1t1000000000000s001B_abc123def456",
                  "status": 400,
                  "error": {"code": -2010, "msg": "Insufficient balance"}
                }
                """;

        ProcessingResult<?> result = processor.processMessage(msg, ctx());

        assertTrue(result.isSuccess());
        OrderDataDto dto = (OrderDataDto) result.getData().orElseThrow();
        assertEquals(OrderDataDto.OrderStatus.REJECTED, dto.status());
        assertTrue(dto.rejectReason().contains("Insufficient balance"));
        assertTrue(publishedEvents.isEmpty(), "order rejection must not fire WebSocketFailedEvent");
    }

    @Test
    void subscriptionConfirmation_returnsIgnored_noEvent() {
        String msg = """
                {
                  "id": "some-uuid",
                  "status": 200,
                  "result": {"subscriptionId": 0}
                }
                """;

        ProcessingResult<?> result = processor.processMessage(msg, ctx());

        assertFalse(result.isError());
        assertFalse(result.isSuccess());
        assertTrue(publishedEvents.isEmpty());
    }

    @Test
    void subscriptionRejection_firesWebSocketFailedEvent() {
        String msg = """
                {
                  "id": "some-uuid",
                  "status": 401,
                  "error": {"code": -2015, "msg": "Invalid API-key"}
                }
                """;

        ProcessingResult<?> result = processor.processMessage(msg, ctx());

        assertFalse(result.isSuccess(), "subscription rejection should not succeed");
        assertEquals(1, publishedEvents.size(), "WebSocketFailedEvent must be published");
    }
}
