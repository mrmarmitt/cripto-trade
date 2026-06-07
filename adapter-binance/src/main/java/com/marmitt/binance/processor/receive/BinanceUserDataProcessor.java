package com.marmitt.binance.processor.receive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.events.WebSocketFailedEvent;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.ProcessorResponse;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ReceivedMessageProcessorPort;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class BinanceUserDataProcessor implements ReceivedMessageProcessorPort {

    private final ObjectMapper objectMapper;
    private final EventPublisherPort eventPublisher;
    private final Map<String, BinanceEventProcessor<?>> processorsByEventType;

    public BinanceUserDataProcessor(ObjectMapper objectMapper, EventPublisherPort eventPublisher) {
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;

        List<BinanceEventProcessor<?>> processors = List.of(
                new ExecutionReportProcessor(objectMapper)
        );

        this.processorsByEventType = processors.stream()
                .collect(Collectors.toMap(BinanceEventProcessor::eventType, Function.identity()));
    }

    @Override
    public ProcessingResult<? extends ProcessorResponse> processMessage(String rawMessage, MessageContext context) {
        String correlationId = context.correlationId().toString();
        try {
            JsonNode root = objectMapper.readTree(rawMessage);

            if (root.has("status") && root.has("id")) {
                return handleSubscriptionConfirmation(root, correlationId, rawMessage, context);
            }

            JsonNode eventNode = root.has("event") ? root.get("event") : root;

            JsonNode eventTypeNode = eventNode.get("e");
            if (eventTypeNode == null) {
                log.debug("User data message with no event type: correlationId={}", correlationId);
                return ProcessingResult.error(correlationId, "User data message has no event type field", rawMessage);
            }

            String eventType = eventTypeNode.asText();
            if ("serverShutdown".equals(eventType)) {
                log.warn("Binance server shutdown notification received — triggering proactive reconnect: exchange={}",
                        context.exchangeName());
                eventPublisher.publishEvent(WebSocketFailedEvent.of(
                        context.exchangeName(),
                        "Binance server shutdown",
                        context.connectionId(),
                        null,
                        context.streamChannel()));
                return ProcessingResult.ignored(correlationId, "server-shutdown");
            }

            BinanceEventProcessor<?> processor = processorsByEventType.get(eventType);

            if (processor == null) {
                log.debug("No processor for user data event type '{}': correlationId={}", eventType, correlationId);
                return ProcessingResult.error(correlationId,
                        "No processor registered for user data event type: " + eventType, rawMessage);
            }

            return processor.process(eventNode, context);

        } catch (Exception e) {
            log.error("Failed to parse user data message: correlationId={}", correlationId, e);
            return ProcessingResult.error(correlationId, "Failed to parse user data message: " + e.getMessage(), rawMessage, e);
        }
    }

    private ProcessingResult<? extends ProcessorResponse> handleSubscriptionConfirmation(
            JsonNode root, String correlationId, String rawMessage, MessageContext context) {
        int status = root.path("status").asInt();
        if (status == 200) {
            log.info("User data stream subscription confirmed: subscriptionId={}",
                    root.path("result").path("subscriptionId").asText("unknown"));
            return ProcessingResult.ignored(correlationId, "subscription-confirmed");
        }
        log.error("User data stream subscription rejected by Binance: status={} exchange={} connectionId={}",
                status, context.exchangeName(), context.connectionId());
        eventPublisher.publishEvent(WebSocketFailedEvent.of(
                context.exchangeName(),
                "User data stream subscription rejected: status=" + status,
                context.connectionId(),
                null,
                context.streamChannel()));
        return ProcessingResult.error(correlationId, "Subscription rejected by Binance: status=" + status, rawMessage);
    }
}
