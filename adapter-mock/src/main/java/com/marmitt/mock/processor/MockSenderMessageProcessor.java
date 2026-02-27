package com.marmitt.mock.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.request.MessageRequest;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.exchange.adapter.SenderMessageProcessorPort;
import com.marmitt.mock.balance.MockBalanceStore;
import com.marmitt.mock.config.MockScenarioConfig;
import com.marmitt.mock.simulator.MockOrderExecutionSimulator;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Processor for outgoing mock messages.
 */
@Slf4j
public class MockSenderMessageProcessor implements SenderMessageProcessorPort {

    private final EventPublisherPort eventPublisher;
    private final ObjectMapper objectMapper;
    private final MockOrderExecutionSimulator simulator;
    private final UUID mockConnectionId;
    private final MockScenarioConfig config;
    private final Random random;
    private final MockBalanceStore balanceStore;

    public MockSenderMessageProcessor(
            EventPublisherPort eventPublisher,
            ObjectMapper objectMapper,
            MockOrderExecutionSimulator simulator,
            MockScenarioConfig config
    ) {
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.simulator = simulator;
        this.mockConnectionId = UUID.randomUUID();
        this.config = config;
        this.random = new Random(config.randomSeed());
        this.balanceStore = new MockBalanceStore(config.balances().initialBalances());
    }

    @Override
    public String execute(MessageRequest request) {
        log.debug("Mock sender processing request - Type: {}", request.getClass().getSimpleName());

        if (request instanceof SendOrderRequest orderRequest) {
            return handleOrderRequest(orderRequest);
        }

        log.debug("Mock ignoring non-order request: {}", request.getClass().getSimpleName());
        return "{\"status\":\"ignored\",\"message\":\"Mock only processes SendOrderRequest\"}";
    }

    private String handleOrderRequest(SendOrderRequest orderRequest) {
        log.info("Mock processing order - ClientOrderId: {}, Symbol: {}, Side: {}, Quantity: {}",
                orderRequest.getClientOrderId(), orderRequest.getSymbol(),
                orderRequest.getOrderSide(), orderRequest.getQuantity());

        CompletableFuture.runAsync(() -> simulateOrderExecutionAsync(orderRequest))
                .exceptionally(throwable -> {
                    log.error("Error in async order simulation - ClientOrderId: {}, Error: {}",
                            orderRequest.getClientOrderId(), throwable.getMessage(), throwable);
                    return null;
                });

        return String.format(
                "{\"status\":\"submitted\",\"clientOrderId\":\"%s\",\"message\":\"Order submitted to mock exchange\"}",
                orderRequest.getClientOrderId()
        );
    }

    private void simulateOrderExecutionAsync(SendOrderRequest orderRequest) {
        try {
            String orderId = "MOCK_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            List<OrderDataDto> events = simulator.buildScenarioEvents(orderRequest, orderId, config, random, balanceStore);

            for (OrderDataDto event : events) {
                sleepLatency();
                publishMockOrderResponse(event);
            }

            OrderDataDto last = events.isEmpty() ? null : events.get(events.size() - 1);
            log.info("Mock order simulation completed - ClientOrderId: {}, OrderId: {}, Status: {}",
                    orderRequest.getClientOrderId(), orderId, last != null ? last.status() : "NONE");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Mock order simulation interrupted - ClientOrderId: {}",
                    orderRequest.getClientOrderId());
        } catch (Exception e) {
            log.error("Error simulating order execution - ClientOrderId: {}, Error: {}",
                    orderRequest.getClientOrderId(), e.getMessage(), e);
        }
    }

    private void sleepLatency() throws InterruptedException {
        long min = config.timing().latencyMinMs();
        long max = config.timing().latencyMaxMs();
        long range = max - min + 1;
        long delta = (random.nextLong() & Long.MAX_VALUE) % range;
        long delay = min == max ? min : min + delta;
        Thread.sleep(delay);
    }

    private void publishMockOrderResponse(OrderDataDto orderResponse) {
        try {
            String mockResponseJson = objectMapper.writeValueAsString(orderResponse);
            MessageContext context = MessageContext.create("MOCK", mockConnectionId);
            Object event = createRawMessageReceivedEvent(mockResponseJson, context);
            eventPublisher.publishEvent(event);

            log.debug("Mock order response event published - OrderId: {}, ClientOrderId: {}",
                    orderResponse.orderId(), orderResponse.clientOrderId());

        } catch (Exception e) {
            log.error("Failed to publish mock order response - OrderId: {}, Error: {}",
                    orderResponse.orderId(), e.getMessage(), e);
        }
    }

    private Object createRawMessageReceivedEvent(String rawMessage, MessageContext context) {
        try {
            Class<?> eventClass = Class.forName("com.marmitt.application.spring.event.RawMessageReceivedEvent");
            return eventClass.getConstructor(Object.class, String.class, MessageContext.class)
                    .newInstance(this, rawMessage, context);
        } catch (Exception e) {
            log.error("Failed to create RawMessageReceivedEvent: {}", e.getMessage());
            throw new RuntimeException("Cannot create event for mock response", e);
        }
    }
}
