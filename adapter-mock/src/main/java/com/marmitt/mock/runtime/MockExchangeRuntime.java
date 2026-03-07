package com.marmitt.mock.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.domain.Symbol;
import com.marmitt.core.dto.websocket.data.AccountDataDto;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.request.SendCancelOrderRequest;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.dto.websocket.request.StreamSubscriptionRequest;
import com.marmitt.core.enums.StreamAction;
import com.marmitt.core.exceptions.ExchangeQueryException;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.mock.balance.MockBalanceStore;
import com.marmitt.mock.config.MockOrderScenarioOverride;
import com.marmitt.mock.config.MockScenarioConfig;
import com.marmitt.mock.processor.MockRawMessagePublisher;
import com.marmitt.mock.simulator.MockMarketDataFeedEngine;
import com.marmitt.mock.simulator.MockOrderExecutionSimulator;
import com.marmitt.mock.simulator.MockScheduledOrderEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Map<String, String> orderIdByClientOrderId = new ConcurrentHashMap<>();
    private final Map<String, OrderDataDto> latestEventByOrderId = new ConcurrentHashMap<>();
    private final Map<String, MockOrderScenarioOverride> orderScenarioOverrideByClientOrderId = new ConcurrentHashMap<>();
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
        OrderDataDto accepted = submitOrderRest(orderRequest);
        return String.format(
                "{\"status\":\"submitted\",\"clientOrderId\":\"%s\",\"orderId\":\"%s\",\"message\":\"Order submitted to mock exchange\"}",
                orderRequest.getClientOrderId(), accepted.orderId()
        );
    }

    public OrderDataDto submitOrderRest(SendOrderRequest orderRequest) {
        if (!lifecycle.isRunning()) {
            return rejectedSnapshot(orderRequest, "MOCK_LIFECYCLE_STOPPED");
        }

        log.info("Mock processing order - ClientOrderId: {}, Symbol: {}, Side: {}, Quantity: {}",
                orderRequest.getClientOrderId(), orderRequest.getSymbol(),
                orderRequest.getOrderSide(), orderRequest.getQuantity());

        String orderId = generateOrderId();
        orderIdByClientOrderId.put(orderRequest.getClientOrderId(), orderId);

        OrderDataDto accepted = simulator.simulateAccepted(orderRequest, orderId);
        latestEventByOrderId.put(orderId, accepted);

        CompletableFuture.runAsync(() -> simulateOrderExecutionAsync(orderRequest, orderId))
                .exceptionally(throwable -> {
                    log.error("Error in async order simulation - ClientOrderId: {}, Error: {}",
                            orderRequest.getClientOrderId(), throwable.getMessage(), throwable);
                    return null;
                });

        return accepted;
    }

    public OrderDataDto cancelOrderRest(SendCancelOrderRequest request) {
        if (!lifecycle.isRunning()) {
            return rejectedCancelSnapshot(request, "MOCK_LIFECYCLE_STOPPED");
        }

        OrderDataDto current = latestEventByOrderId.get(request.getOrderId());
        if (current == null) {
            return rejectedCancelSnapshot(request, "ORDER_NOT_FOUND");
        }
        if (isTerminal(current.status())) {
            return current;
        }

        OrderDataDto canceled = new OrderDataDto(
                current.orderId(),
                current.clientOrderId(),
                current.symbol(),
                current.side(),
                current.type(),
                current.quantity(),
                current.executedQuantity(),
                current.price(),
                current.executedPrice(),
                current.fee(),
                OrderDataDto.OrderStatus.CANCELED,
                null,
                java.time.Instant.now()
        );

        publishMockOrderResponse(canceled);
        return canceled;
    }

    public Optional<OrderDataDto> queryOrderByClientOrderId(String symbol, String clientOrderId) {
        ensureLifecycleForQuery();
        String orderId = orderIdByClientOrderId.get(clientOrderId);
        if (orderId == null) {
            return Optional.empty();
        }
        return queryOrderByExchangeOrderId(symbol, orderId);
    }

    public Optional<OrderDataDto> queryOrderByExchangeOrderId(String symbol, String exchangeOrderId) {
        ensureLifecycleForQuery();
        OrderDataDto order = latestEventByOrderId.get(exchangeOrderId);
        if (order == null) {
            return Optional.empty();
        }
        if (symbol != null && !symbol.isBlank() && !order.symbol().value().equalsIgnoreCase(symbol)) {
            return Optional.empty();
        }
        return Optional.of(order);
    }

    public List<OrderDataDto> listOpenOrdersBySymbol(String symbol) {
        return latestEventByOrderId.values().stream()
                .filter(order -> !isTerminal(order.status()))
                .filter(order -> order.symbol().value().equalsIgnoreCase(symbol))
                .toList();
    }

    public List<OrderDataDto> listAllOpenOrders() {
        return latestEventByOrderId.values().stream()
                .filter(order -> !isTerminal(order.status()))
                .toList();
    }

    public AccountDataDto queryAccountSnapshot() {
        Map<String, java.math.BigDecimal> available = balanceStore.snapshotAvailable();
        Map<String, java.math.BigDecimal> reserved = balanceStore.snapshotReserved();
        return new AccountDataDto(
                "mock-account",
                available,
                reserved,
                java.time.Instant.now()
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

    /**
     * Registers a one-shot deterministic scenario for an order.
     * The override is consumed when that clientOrderId is submitted.
     */
    public void registerOrderScenarioOverride(String clientOrderId, MockOrderScenarioOverride override) {
        if (clientOrderId == null || clientOrderId.isBlank()) {
            throw new IllegalArgumentException("clientOrderId cannot be null or blank");
        }
        if (override == null) {
            throw new IllegalArgumentException("override cannot be null");
        }
        orderScenarioOverrideByClientOrderId.put(clientOrderId, override);
        log.info("Mock scenario override registered - ClientOrderId: {}, events: {}",
                clientOrderId, override.events().size());
    }

    public void clearOrderScenarioOverride(String clientOrderId) {
        if (clientOrderId == null || clientOrderId.isBlank()) {
            return;
        }
        orderScenarioOverrideByClientOrderId.remove(clientOrderId);
    }

    public void clearOrderScenarioOverrides() {
        orderScenarioOverrideByClientOrderId.clear();
    }

    private void simulateOrderExecutionAsync(SendOrderRequest orderRequest, String orderId) {
        try {
            Random localRandom = this.random;
            MockBalanceStore localBalanceStore = this.balanceStore;
            MockOrderScenarioOverride override = orderScenarioOverrideByClientOrderId.remove(orderRequest.getClientOrderId());
            List<MockScheduledOrderEvent> scheduledEvents = simulator.buildScenarioSchedule(
                    orderRequest, orderId, config, localRandom, localBalanceStore, override);

            for (MockScheduledOrderEvent scheduledEvent : scheduledEvents) {
                sleepForEvent(scheduledEvent);
                publishMockOrderResponse(scheduledEvent.orderData());
            }

            OrderDataDto last = scheduledEvents.isEmpty() ? null : scheduledEvents.get(scheduledEvents.size() - 1).orderData();
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

    private void sleepForEvent(MockScheduledOrderEvent scheduledEvent) throws InterruptedException {
        long plannedDelay = scheduledEvent.delayBeforeMs();
        if (plannedDelay >= 0) {
            if (plannedDelay > 0) {
                Thread.sleep(plannedDelay);
            }
            return;
        }
        sleepLatency();
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
            latestEventByOrderId.put(orderResponse.orderId(), orderResponse);
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
        this.orderIdByClientOrderId.clear();
        this.latestEventByOrderId.clear();
        this.orderScenarioOverrideByClientOrderId.clear();
    }

    private void ensureLifecycleForQuery() {
        if (lifecycle.isRunning()) {
            return;
        }
        throw new ExchangeQueryException(
                "MOCK",
                ExchangeQueryException.ErrorType.TEMPORARY,
                "Mock lifecycle is stopped for order query"
        );
    }

    private static boolean isTerminal(OrderDataDto.OrderStatus status) {
        return status == OrderDataDto.OrderStatus.FILLED
                || status == OrderDataDto.OrderStatus.CANCELED
                || status == OrderDataDto.OrderStatus.EXPIRED
                || status == OrderDataDto.OrderStatus.REJECTED;
    }

    private static String generateOrderId() {
        return "MOCK_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private static OrderDataDto rejectedSnapshot(SendOrderRequest request, String reason) {
        Symbol symbol = Symbol.of(request.getSymbol());
        OrderDataDto.OrderSide side = request.getOrderSide() == com.marmitt.core.enums.OrderSide.BUY
                ? OrderDataDto.OrderSide.BUY
                : OrderDataDto.OrderSide.SELL;
        OrderDataDto.OrderType type = switch (request.getOrderType()) {
            case MARKET -> OrderDataDto.OrderType.MARKET;
            case LIMIT -> OrderDataDto.OrderType.LIMIT;
            case STOP_LOSS, STOP_LOSS_LIMIT -> OrderDataDto.OrderType.STOP;
            case TAKE_PROFIT, TAKE_PROFIT_LIMIT -> OrderDataDto.OrderType.STOP_LIMIT;
        };
        return new OrderDataDto(
                "MOCK_REJECTED",
                request.getClientOrderId(),
                symbol,
                side,
                type,
                request.getQuantity(),
                java.math.BigDecimal.ZERO,
                request.getPrice(),
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                OrderDataDto.OrderStatus.REJECTED,
                reason,
                java.time.Instant.now()
        );
    }

    private static OrderDataDto rejectedCancelSnapshot(SendCancelOrderRequest request, String reason) {
        String symbol = request.getSymbol() == null || request.getSymbol().isBlank()
                ? "UNKNOWNUSDT"
                : request.getSymbol();
        return new OrderDataDto(
                request.getOrderId(),
                null,
                Symbol.of(symbol),
                OrderDataDto.OrderSide.SELL,
                OrderDataDto.OrderType.LIMIT,
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                OrderDataDto.OrderStatus.REJECTED,
                reason,
                java.time.Instant.now()
        );
    }
}
