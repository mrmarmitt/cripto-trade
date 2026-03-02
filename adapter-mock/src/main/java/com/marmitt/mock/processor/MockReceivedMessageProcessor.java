package com.marmitt.mock.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.MarketDataDto;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.data.ProcessorResponse;
import com.marmitt.core.ports.outbound.exchange.adapter.ReceivedMessageProcessorPort;
import lombok.extern.slf4j.Slf4j;

/**
 * Processor para mensagens recebidas do Mock Adapter.
 * Deserializa respostas de ordem e ticks de market data.
 */
@Slf4j
public class MockReceivedMessageProcessor implements ReceivedMessageProcessorPort {

    private final ObjectMapper objectMapper;

    public MockReceivedMessageProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ProcessingResult<? extends ProcessorResponse> processMessage(String rawMessage, MessageContext context) {
        log.debug("Mock processing received message - Length: {}, Exchange: {}",
                rawMessage.length(), context.exchangeName());

        try {
            JsonNode json = objectMapper.readTree(rawMessage);
            ProcessorResponse payload = resolvePayload(json, rawMessage);

            if (payload instanceof OrderDataDto orderData) {
                log.info("Mock order message processed - OrderId: {}, ClientOrderId: {}, Status: {}",
                        orderData.orderId(), orderData.clientOrderId(), orderData.status());
                String correlationId = orderData.clientOrderId() != null
                        ? orderData.clientOrderId()
                        : orderData.orderId();
                return ProcessingResult.success(correlationId, rawMessage, orderData);
            }

            if (payload instanceof MarketDataDto marketData) {
                log.debug("Mock market message processed - Symbol: {}, Price: {}",
                        marketData.symbol(), marketData.price());
                String correlationId = marketData.exchangeName() + "-" + marketData.symbol().value() + "-" + marketData.timestamp();
                return ProcessingResult.success(correlationId, rawMessage, marketData);
            }

            throw new IllegalStateException("Unsupported payload type: " + payload.getClass().getSimpleName());

        } catch (Exception e) {
            log.error("Failed to process mock message - Error: {}, Message: {}",
                    e.getMessage(), rawMessage, e);

            String correlationId = "MOCK-ERROR-" + System.currentTimeMillis();
            return ProcessingResult.error(
                    correlationId,
                    "Failed to parse mock order response: " + e.getMessage(),
                    rawMessage,
                    e
            );
        }
    }

    private ProcessorResponse resolvePayload(JsonNode json, String rawMessage) throws Exception {
        if (isOrderPayload(json)) {
            return objectMapper.readValue(rawMessage, OrderDataDto.class);
        }
        if (isMarketPayload(json)) {
            // MarketDataDto has computed getters (midPrice/spread) that may appear in payload;
            // tolerate unknown fields in mock feed parsing.
            return objectMapper.readerFor(MarketDataDto.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(rawMessage);
        }
        throw new IllegalArgumentException("Mock message type not recognized");
    }

    private boolean isOrderPayload(JsonNode json) {
        return json.has("status") && json.has("clientOrderId");
    }

    private boolean isMarketPayload(JsonNode json) {
        return json.has("exchangeName") && json.has("symbol") && json.has("price");
    }
}
