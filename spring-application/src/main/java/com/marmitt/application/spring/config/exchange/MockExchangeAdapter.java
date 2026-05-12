package com.marmitt.application.spring.config.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.websocket.data.AccountDataDto;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.request.SendCancelOrderRequest;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.dto.websocket.request.StreamSubscriptionRequest;
import com.marmitt.core.dto.exchange.boot.ExchangeBootReadiness;
import com.marmitt.core.exceptions.ExchangeQueryException.ErrorType;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.ProcessorResponse;
import com.marmitt.core.dto.websocket.request.MessageRequest;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ReceivedMessageProcessorPort;
import com.marmitt.core.ports.outbound.exchange.adapter.SenderMessageProcessorPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeAccountQueryPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeBootReadinessPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderExecutionPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderQueryPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeStreamingPort;
import com.marmitt.mock.config.MockBootReadinessConfig;
import com.marmitt.mock.config.MockOrderScenarioOverride;
import com.marmitt.mock.config.MockMarketDataFeedConfig;
import com.marmitt.mock.config.MockScenarioConfig;
import com.marmitt.mock.processor.MockReceivedMessageProcessor;
import com.marmitt.mock.processor.MockSenderMessageProcessor;
import com.marmitt.mock.runtime.MockExchangeRuntime;
import com.marmitt.mock.simulator.MockMarketDataFeedEngine;
import com.marmitt.mock.simulator.MockOrderExecutionSimulator;

import java.util.List;
import java.util.Optional;

/**
 * Mock exchange adapter used for local simulation.
 *
 * <p>Stage 2 capability status:
 * <ul>
 *   <li>Streaming: implemented (local event websocket).</li>
 *   <li>Order execution/query/account query: implemented via {@link MockExchangeRuntime}.</li>
 * </ul>
 */
public class MockExchangeAdapter implements ExchangeStreamingPort,
        ExchangeOrderExecutionPort,
        ExchangeOrderQueryPort,
        ExchangeAccountQueryPort,
        ExchangeBootReadinessPort {

    private final ReceivedMessageProcessorPort receivedMessageProcessor;
    private final SenderMessageProcessorPort senderMessageProcessor;
    private final MockExchangeRuntime runtime;
    private final MockBootReadinessConfig bootReadinessConfig;

    public MockExchangeAdapter(ObjectMapper objectMapper, EventPublisherPort eventPublisher) {
        this.receivedMessageProcessor = new MockReceivedMessageProcessor(objectMapper);

        MockOrderExecutionSimulator simulator = new MockOrderExecutionSimulator();
        MockScenarioConfig config = MockScenarioConfig.defaultConfig();
        this.bootReadinessConfig = MockBootReadinessConfig.defaultConfig();
        MockMarketDataFeedConfig feedConfig = MockMarketDataFeedConfig.defaultConfig();
        MockMarketDataFeedEngine feedEngine = new MockMarketDataFeedEngine(
                eventPublisher,
                objectMapper,
                feedConfig,
                config.randomSeed()
        );
        this.runtime = new MockExchangeRuntime(
                eventPublisher,
                objectMapper,
                simulator,
                config,
                feedEngine
        );
        this.senderMessageProcessor = new MockSenderMessageProcessor(runtime);
    }

    @Override
    public String getExchangeName() {
        return "MOCK";
    }

    @Override
    public boolean requiresPostConnection() {
        return true;
    }

    @Override
    public String buildConnectionUrl(StreamSubscriptionRequest parameters, String exchangeName) {
        return "mock://localhost";
    }

    @Override
    public String formatMessage(MessageRequest request) {
        return senderMessageProcessor.execute(request);
    }

    @Override
    public ProcessingResult<? extends ProcessorResponse> processMessage(String rawMessage, MessageContext context) {
        return receivedMessageProcessor.processMessage(rawMessage, context);
    }

    @Override
    public OrderDataDto submitOrder(SendOrderRequest request) {
        return runtime.submitOrderRest(request);
    }

    @Override
    public OrderDataDto cancelOrder(SendCancelOrderRequest request) {
        return runtime.cancelOrderRest(request);
    }

    @Override
    public Optional<OrderDataDto> queryOrderByClientOrderId(String symbol, String clientOrderId) {
        return runtime.queryOrderByClientOrderId(symbol, clientOrderId);
    }

    @Override
    public Optional<OrderDataDto> queryOrderByExchangeOrderId(String symbol, String exchangeOrderId) {
        return runtime.queryOrderByExchangeOrderId(symbol, exchangeOrderId);
    }

    @Override
    public List<OrderDataDto> listOpenOrdersBySymbol(String symbol) {
        return runtime.listOpenOrdersBySymbol(symbol);
    }

    @Override
    public List<OrderDataDto> listAllOpenOrders() {
        return runtime.listAllOpenOrders();
    }

    @Override
    public AccountDataDto queryAccountSnapshot() {
        return runtime.queryAccountSnapshot();
    }

    @Override
    public ExchangeBootReadiness checkBootReadiness() {
        if (!bootReadinessConfig.enabled()) {
            return ExchangeBootReadiness.ready("MOCK", "Mock readiness check disabled.");
        }

        simulateDelayIfNeeded(bootReadinessConfig.simulatedDelayMs());

        return switch (bootReadinessConfig.mode()) {
            case READY -> ExchangeBootReadiness.ready("MOCK", bootReadinessConfig.message());
            case CONNECTIVITY_FAIL -> ExchangeBootReadiness.notReady(
                    "MOCK", "CONNECTIVITY_FAIL", bootReadinessConfig.message());
            case AUTH_FAIL -> ExchangeBootReadiness.notReady(
                    "MOCK", "AUTH_FAIL", bootReadinessConfig.message());
            case TIMEOUT -> ExchangeBootReadiness.notReady(
                    "MOCK", "TIMEOUT", bootReadinessConfig.message());
        };
    }

    /**
     * Registers a deterministic one-shot scenario for a specific clientOrderId.
     * Useful for integration tests that need strict control over order events.
     */
    public void registerOrderScenarioOverride(String clientOrderId, MockOrderScenarioOverride override) {
        runtime.registerOrderScenarioOverride(clientOrderId, override);
    }

    public void clearOrderScenarioOverride(String clientOrderId) {
        runtime.clearOrderScenarioOverride(clientOrderId);
    }

    public void clearOrderScenarioOverrides() {
        runtime.clearOrderScenarioOverrides();
    }

    /**
     * Seeds a deterministic REST query snapshot without emitting callbacks.
     * Intended for boot recovery integration tests that exercise limbo reconciliation.
     */
    public void seedQueriedOrderSnapshot(OrderDataDto orderData) {
        runtime.seedQueriedOrderSnapshot(orderData);
    }

    /**
     * Registers a deterministic fail-N-times plan for query-by-clientOrderId.
     * Intended for boot recovery retry/backoff integration tests.
     */
    public void registerQueryFailurePlan(String clientOrderId,
                                         int failuresBeforeSuccess,
                                         ErrorType errorType,
                                         String message) {
        runtime.registerQueryFailurePlan(clientOrderId, failuresBeforeSuccess, errorType, message);
    }

    public void clearQueryFailurePlans() {
        runtime.clearQueryFailurePlans();
    }

    private static void simulateDelayIfNeeded(long delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
