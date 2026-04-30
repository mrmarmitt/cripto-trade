package com.marmitt.coinbase.processor.receive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.ProcessorResponse;
import com.marmitt.core.ports.outbound.exchange.adapter.ReceivedMessageProcessorPort;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class CoinbaseReceivedMessageProcessor implements ReceivedMessageProcessorPort {

    private final ObjectMapper objectMapper;
    private final Map<String, CoinbaseEventProcessor<?>> processorsByEventType;

    public CoinbaseReceivedMessageProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        List<CoinbaseEventProcessor<?>> processors = List.of(
                new TickerProcessor(objectMapper)
        );

        this.processorsByEventType = processors.stream()
                .collect(Collectors.toMap(CoinbaseEventProcessor::eventType, Function.identity()));
    }

    @Override
    public ProcessingResult<? extends ProcessorResponse> processMessage(String rawMessage, MessageContext context) {
        String correlationId = context.correlationId().toString();
        try {
            JsonNode data = objectMapper.readTree(rawMessage);
            JsonNode typeNode = data.get("type");

            if (typeNode == null) {
                log.debug("Unrecognized Coinbase message with no type field: correlationId={}", correlationId);
                return ProcessingResult.error(correlationId, "Unrecognized message: no type field", rawMessage);
            }

            String eventType = typeNode.asText();
            CoinbaseEventProcessor<?> processor = processorsByEventType.get(eventType);

            if (processor == null) {
                log.debug("No processor for Coinbase event type '{}': correlationId={}", eventType, correlationId);
                return ProcessingResult.error(correlationId, "No processor registered for event type: " + eventType, rawMessage);
            }

            return processor.process(data, context);

        } catch (Exception e) {
            log.error("Failed to parse Coinbase message: correlationId={}", correlationId, e);
            return ProcessingResult.error(correlationId, "Failed to parse message: " + e.getMessage(), rawMessage, e);
        }
    }
}
