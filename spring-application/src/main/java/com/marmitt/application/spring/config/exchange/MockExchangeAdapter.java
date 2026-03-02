package com.marmitt.application.spring.config.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.websocket.data.AccountDataDto;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.request.SendCancelOrderRequest;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.dto.websocket.request.StreamSubscriptionRequest;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ExchangeUrlBuilderPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ReceivedMessageProcessorPort;
import com.marmitt.core.ports.outbound.exchange.adapter.SenderMessageProcessorPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeAccountQueryPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderExecutionPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderQueryPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeStreamingPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPort;
import com.marmitt.mock.adapter.LocalEventWebSocketAdapter;
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
        ExchangeAccountQueryPort {

    private final WebSocketPort webSocketPort;
    private final ReceivedMessageProcessorPort receivedMessageProcessor;
    private final SenderMessageProcessorPort senderMessageProcessor;
    private final ExchangeUrlBuilderPort urlBuilder;
    private final MockExchangeRuntime runtime;

    public MockExchangeAdapter(ObjectMapper objectMapper, EventPublisherPort eventPublisher) {
        this.webSocketPort = new LocalEventWebSocketAdapter(eventPublisher);
        this.receivedMessageProcessor = new MockReceivedMessageProcessor(objectMapper);

        MockOrderExecutionSimulator simulator = new MockOrderExecutionSimulator();
        MockScenarioConfig config = MockScenarioConfig.defaultConfig();
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
        this.urlBuilder = new NoOpUrlBuilder();
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
    public WebSocketPort getWebSocketPort() {
        return webSocketPort;
    }

    @Override
    public ReceivedMessageProcessorPort getReceivedMessageProcessor() {
        return receivedMessageProcessor;
    }

    @Override
    public SenderMessageProcessorPort getSenderMessageProcessor() {
        return senderMessageProcessor;
    }

    @Override
    public ExchangeUrlBuilderPort getUrlBuilder() {
        return urlBuilder;
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

    private static class NoOpUrlBuilder implements ExchangeUrlBuilderPort {
        @Override
        public String buildConnectionUrl(StreamSubscriptionRequest parameters) {
            return "mock://localhost";
        }
    }
}
