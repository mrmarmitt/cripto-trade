package com.marmitt.mock.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.data.ProcessorResponse;
import com.marmitt.core.ports.outbound.exchange.adapter.ReceivedMessageProcessorPort;
import lombok.extern.slf4j.Slf4j;

/**
 * Processor para mensagens recebidas do Mock Adapter.
 * Deserializa respostas de ordens mockadas.
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
            // Deserializar JSON mockado para OrderDataDto
            OrderDataDto orderData = objectMapper.readValue(rawMessage, OrderDataDto.class);

            log.info("Mock message processed successfully - OrderId: {}, ClientOrderId: {}, Status: {}",
                    orderData.orderId(), orderData.clientOrderId(), orderData.status());

            String correlationId = orderData.clientOrderId() != null
                    ? orderData.clientOrderId()
                    : orderData.orderId();

            return ProcessingResult.success(correlationId, rawMessage, orderData);

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
}
