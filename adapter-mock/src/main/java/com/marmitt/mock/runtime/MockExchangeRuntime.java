package com.marmitt.mock.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.dto.websocket.request.StreamSubscriptionRequest;
import com.marmitt.core.enums.StreamAction;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.mock.balance.MockBalanceStore;
import com.marmitt.mock.config.MockScenarioConfig;
import com.marmitt.mock.processor.MockRawMessagePublisher;
import com.marmitt.mock.simulator.MockMarketDataFeedEngine;
import com.marmitt.mock.simulator.MockOrderExecutionSimulator;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Runtime/orchestrator for the mock exchange.
 *
 * <p>Centralizes command handling for:
 * - order execution simulation
 * - market-data feed subscribe/unsubscribe
 */
@Slf4j
public class MockExchangeRuntime {

    private final MockOrderExecutionSimulator simulator;
    private final MockScenarioConfig config;
    private final long seed;
    private final MockMarketDataFeedEngine marketDataFeedEngine;
    private final MockRawMessagePublisher rawMessagePublisher;
    private final MockLifecycle lifecycle;
    private Random random;
    private MockBalanceStore balanceStore;

    public MockExchangeRuntime(EventPublisherPort eventPublisher,
                               ObjectMapper objectMapper,
                               MockOrderExecutionSimulator simulator,
                               MockScenarioConfig config,
                               MockMarketDataFeedEngine marketDataFeedEngine) {
        this.simulator = simulator;
        this.config = config;
        this.seed = config.randomSeed();
        this.random = new Random(seed);
        this.balanceStore = new MockBalanceStore(config.balances().initialBalances());
        this.marketDataFeedEngine = marketDataFeedEngine;
        this.rawMessagePublisher = new MockRawMessagePublisher(
                eventPublisher,
                objectMapper,
                "MOCK",
                UUID.randomUUID()
        );
        this.lifecycle = new MockLifecycle(
                true,
                marketDataFeedEngine::reset,
                this::resetInternal
        );
    }

    public String submitOrder(SendOrderRequest orderRequest) {
        if (!lifecycle.isRunning()) {
            return "{\"status\":\"ignored\",\"reason\":\"mock lifecycle stopped\"}";
        }

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

    public String handleStream(StreamSubscriptionRequest request) {
        if (!lifecycle.isRunning()) {
            return "{\"status\":\"ignored\",\"reason\":\"mock lifecycle stopped\"}";
        }
        if (request.getStreamAction() == StreamAction.SUBSCRIBE) {
            marketDataFeedEngine.subscribe(request.getCurrencyPairs());
            log.info("Mock feed subscribe - pairs={}", request.getCurrencyPairs().size());
            return "{\"status\":\"subscribed\",\"exchange\":\"MOCK\"}";
        }
        marketDataFeedEngine.unsubscribe(request.getCurrencyPairs());
        log.info("Mock feed unsubscribe - pairs={}", request.getCurrencyPairs().size());
        return "{\"status\":\"unsubscribed\",\"exchange\":\"MOCK\"}";
    }

    public void start() {
        lifecycle.start();
    }

    public void stop() {
        lifecycle.stop();
    }

    public void reset() {
        lifecycle.reset();
    }

    public boolean isRunning() {
        return lifecycle.isRunning();
    }

    private void simulateOrderExecutionAsync(SendOrderRequest orderRequest) {
        try {
            Random localRandom = this.random;
            MockBalanceStore localBalanceStore = this.balanceStore;
            String orderId = "MOCK_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            List<OrderDataDto> events = simulator.buildScenarioEvents(
                    orderRequest, orderId, config, localRandom, localBalanceStore);

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
            rawMessagePublisher.publish(orderResponse);
            log.debug("Mock order response event published - OrderId: {}, ClientOrderId: {}",
                    orderResponse.orderId(), orderResponse.clientOrderId());

        } catch (Exception e) {
            log.error("Failed to publish mock order response - OrderId: {}, Error: {}",
                    orderResponse.orderId(), e.getMessage(), e);
        }
    }

    private void resetInternal() {
        marketDataFeedEngine.reset();
        this.random = new Random(seed);
        this.balanceStore = new MockBalanceStore(config.balances().initialBalances());
    }
}
