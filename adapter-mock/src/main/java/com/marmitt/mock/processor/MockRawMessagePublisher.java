package com.marmitt.mock.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.ProcessorResponse;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Publishes mock payloads into the same raw-message event pipeline used by real exchanges.
 */
@Slf4j
public class MockRawMessagePublisher {

    private final EventPublisherPort eventPublisher;
    private final ObjectMapper objectMapper;
    private final String exchangeName;
    private final UUID connectionId;

    public MockRawMessagePublisher(EventPublisherPort eventPublisher,
                                   ObjectMapper objectMapper,
                                   String exchangeName,
                                   UUID connectionId) {
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.exchangeName = exchangeName;
        this.connectionId = connectionId;
    }

    public void publish(ProcessorResponse payload) {
        try {
            String raw = objectMapper.writeValueAsString(payload);
            publishRaw(raw);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize mock payload for publication", e);
        }
    }

    public void publishRaw(String rawMessage) {
        MessageContext context = MessageContext.create(exchangeName, connectionId);
        Object event = createRawMessageReceivedEvent(rawMessage, context);
        eventPublisher.publishEvent(event);
    }

    private Object createRawMessageReceivedEvent(String rawMessage, MessageContext context) {
        try {
            Class<?> eventClass = Class.forName("com.marmitt.application.spring.event.RawMessageReceivedEvent");
            return eventClass.getConstructor(Object.class, String.class, MessageContext.class)
                    .newInstance(this, rawMessage, context);
        } catch (Exception e) {
            log.error("Failed to create RawMessageReceivedEvent: {}", e.getMessage(), e);
            throw new RuntimeException("Cannot create event for mock response", e);
        }
    }
}
