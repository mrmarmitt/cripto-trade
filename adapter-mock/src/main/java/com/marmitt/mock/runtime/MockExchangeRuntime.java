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
import com.marmitt.core.exceptions.ExchangeQueryException.ErrorType;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.mock.balance.MockBalanceStore;
import com.marmitt.mock.config.MockOrderScenarioOverride;
import com.marmitt.mock.config.MockScenarioConfig;
import com.marmitt.mock.processor.MockRawMessagePublisher;
import com.marmitt.mock.simulator.MockMarketDataFeedEngine;
import com.marmitt.mock.simulator.MockOrderExecutionSimulator;
import com.marmitt.mock.simulator.MockReferencePriceStore;
import com.marmitt.mock.simulator.MockScheduledOrderEvent;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
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
    private final Map<String, QueryFailurePlan> queryFailurePlanByClientOrderId = new ConcurrentHashMap<>();
    private final Map<String, RestingOrder> restingOrderByOrderId = new ConcurrentHashMap<>();
    private final MockReferencePriceStore referencePriceStore = new MockReferencePriceStore();
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
        this.marketDataFeedEngine.setPriceListener(this::updateReferencePrice);
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

        if (!orderScenarioOverrideByClientOrderId.containsKey(orderRequest.getClientOrderId())
                && restsOnBook(orderRequest)) {
            return placeRestingOrder(orderRequest, orderId);
        }

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

        String exchangeOrderId = orderIdByClientOrderId.get(request.getClientOrderId());
        OrderDataDto current = exchangeOrderId != null ? latestEventByOrderId.get(exchangeOrderId) : null;
        if (current == null) {
            return rejectedCancelSnapshot(request, "ORDER_NOT_FOUND");
        }
        if (isTerminal(current.status())) {
            return current;
        }

        releaseRestingReservation(exchangeOrderId);

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
        maybeFailQuery(clientOrderId);
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

    /**
     * Registers a deterministic fail-N-times plan for REST order queries by clientOrderId.
     * Used by boot recovery integration tests to validate retry/backoff behavior.
     */
    public void registerQueryFailurePlan(String clientOrderId,
                                         int failuresBeforeSuccess,
                                         ErrorType errorType,
                                         String message) {
        if (clientOrderId == null || clientOrderId.isBlank()) {
            throw new IllegalArgumentException("clientOrderId cannot be null or blank");
        }
        if (failuresBeforeSuccess <= 0) {
            throw new IllegalArgumentException("failuresBeforeSuccess must be > 0");
        }
        queryFailurePlanByClientOrderId.put(
                clientOrderId,
                new QueryFailurePlan(failuresBeforeSuccess, errorType, message)
        );
        log.info("Mock query failure plan registered - ClientOrderId: {}, failuresBeforeSuccess: {}, errorType: {}",
                clientOrderId, failuresBeforeSuccess, errorType);
    }

    public void clearQueryFailurePlans() {
        queryFailurePlanByClientOrderId.clear();
    }

    /**
     * Seeds the latest queried order snapshot without publishing websocket callbacks.
     *
     * <p>Used by integration tests that need deterministic REST query responses for
     * boot recovery scenarios (for example, limbo SUBMITTED/PARTIAL orders found as
     * FILLED on the exchange after application restart).
     */
    public void seedQueriedOrderSnapshot(OrderDataDto orderData) {
        if (orderData == null) {
            throw new IllegalArgumentException("orderData cannot be null");
        }
        if (orderData.clientOrderId() == null || orderData.clientOrderId().isBlank()) {
            throw new IllegalArgumentException("orderData.clientOrderId cannot be null or blank");
        }
        if (orderData.orderId() == null || orderData.orderId().isBlank()) {
            throw new IllegalArgumentException("orderData.orderId cannot be null or blank");
        }

        orderIdByClientOrderId.put(orderData.clientOrderId(), orderData.orderId());
        latestEventByOrderId.put(orderData.orderId(), orderData);
        log.info("Mock queried order snapshot seeded - OrderId: {}, ClientOrderId: {}, Status: {}",
                orderData.orderId(), orderData.clientOrderId(), orderData.status());
    }

    /**
     * Seeds the market reference price used to decide whether a LIMIT order rests.
     *
     * <p>Intended for integration tests that drive ticks straight into the signal pipeline and
     * therefore never exercise the mock feed. Seeding the same price the strategy sees keeps the
     * strategy and the exchange looking at one market.
     *
     * <p>Seeding also re-evaluates orders already resting on that symbol, which is how a test
     * moves the market and makes a resting order fill.
     */
    public void seedReferencePrice(String symbol, BigDecimal price) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol cannot be null or blank");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
        updateReferencePrice(symbol, price);
        log.info("Mock reference price seeded - Symbol: {}, Price: {}", symbol, price);
    }

    private void updateReferencePrice(String symbol, BigDecimal price) {
        referencePriceStore.update(symbol, price);
        crossRestingOrders(symbol, price);
    }

    /**
     * Fills every resting order on the symbol whose limit the market has crossed.
     *
     * <p>Each order is removed from the registry before executing, so a concurrent cancel and a
     * crossing tick cannot both settle the same order.
     */
    private void crossRestingOrders(String symbol, BigDecimal price) {
        if (restingOrderByOrderId.isEmpty()) {
            return;
        }
        for (RestingOrder resting : List.copyOf(restingOrderByOrderId.values())) {
            if (!resting.request().getSymbol().equalsIgnoreCase(symbol)) {
                continue;
            }
            if (simulator.shouldRest(resting.request(), price)) {
                continue;
            }
            if (restingOrderByOrderId.remove(resting.orderId()) == null) {
                continue;
            }
            fillRestingOrder(resting);
        }
    }

    private void fillRestingOrder(RestingOrder resting) {
        try {
            List<MockScheduledOrderEvent> events = simulator.buildRestingFillSchedule(
                    resting.request(), resting.orderId(), config, this.balanceStore);
            for (MockScheduledOrderEvent event : events) {
                publishMockOrderResponse(event.orderData());
            }
            log.info("Mock resting order crossed and filled - ClientOrderId: {}, OrderId: {}",
                    resting.request().getClientOrderId(), resting.orderId());
        } catch (Exception e) {
            log.error("Error filling resting mock order - ClientOrderId: {}, Error: {}",
                    resting.request().getClientOrderId(), e.getMessage(), e);
        }
    }

    private void releaseRestingReservation(String exchangeOrderId) {
        RestingOrder resting = restingOrderByOrderId.remove(exchangeOrderId);
        if (resting == null) {
            return;
        }
        simulator.releaseReservationForOpenOrder(resting.request(), this.balanceStore, config);
        log.info("Mock resting order canceled, reservation released - ClientOrderId: {}, OrderId: {}",
                resting.request().getClientOrderId(), exchangeOrderId);
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

    private boolean restsOnBook(SendOrderRequest orderRequest) {
        BigDecimal reference = referencePriceStore.find(orderRequest.getSymbol()).orElse(null);
        return simulator.shouldRest(orderRequest, reference);
    }

    /**
     * Places an order that does not cross the market.
     *
     * <p>Validation, reservation and registration happen synchronously, before this returns, so
     * that a cancel or a crossing tick arriving right after the REST acknowledgment always finds
     * the order on the book. Only the NEW callback is published asynchronously, matching how the
     * scripted path emits events.
     *
     * <p>No terminal event follows: the order waits for a crossing tick or a cancel.
     */
    private OrderDataDto placeRestingOrder(SendOrderRequest orderRequest, String orderId) {
        MockScheduledOrderEvent placement = simulator
                .buildRestingSchedule(orderRequest, orderId, config, this.balanceStore)
                .getFirst();
        OrderDataDto event = placement.orderData();
        latestEventByOrderId.put(orderId, event);

        if (event.status() != OrderDataDto.OrderStatus.NEW) {
            log.info("Mock order rejected before resting - ClientOrderId: {}, OrderId: {}, Reason: {}",
                    orderRequest.getClientOrderId(), orderId, event.rejectReason());
            publishAsync(orderId, event, false);
            return event;
        }

        restingOrderByOrderId.put(orderId, new RestingOrder(orderRequest, orderId));
        log.info("Mock order resting on book - ClientOrderId: {}, OrderId: {}, LimitPrice: {}",
                orderRequest.getClientOrderId(), orderId, orderRequest.getPrice());
        publishAsync(orderId, event, true);
        return event;
    }

    /**
     * Publishes a resting-order callback after the simulated latency.
     *
     * <p>When {@code onlyWhileResting} is set the callback is dropped if the order left the book
     * meanwhile, so a NEW event can never land after the CANCELED or FILLED that closed it.
     */
    private void publishAsync(String orderId, OrderDataDto event, boolean onlyWhileResting) {
        CompletableFuture.runAsync(() -> {
            try {
                sleepLatency();
                if (onlyWhileResting && !restingOrderByOrderId.containsKey(orderId)) {
                    return;
                }
                publishMockOrderResponse(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Error publishing resting mock order event - OrderId: {}, Error: {}",
                        orderId, e.getMessage(), e);
            }
        });
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
        this.queryFailurePlanByClientOrderId.clear();
        this.restingOrderByOrderId.clear();
        this.referencePriceStore.clear();
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

    private void maybeFailQuery(String clientOrderId) {
        QueryFailurePlan plan = queryFailurePlanByClientOrderId.get(clientOrderId);
        if (plan == null) {
            return;
        }

        if (plan.remainingFailures() <= 1) {
            queryFailurePlanByClientOrderId.remove(clientOrderId);
        } else {
            queryFailurePlanByClientOrderId.put(
                    clientOrderId,
                    new QueryFailurePlan(plan.remainingFailures() - 1, plan.errorType(), plan.message())
            );
        }

        throw new ExchangeQueryException(
                "MOCK",
                plan.errorType(),
                plan.message() != null ? plan.message() : "Planned mock query failure"
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
        return new OrderDataDto(
                request.getClientOrderId(),
                null,
                Symbol.of(request.getSymbol()),
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

    private record QueryFailurePlan(int remainingFailures,
                                    ErrorType errorType,
                                    String message) {
    }

    /**
     * An order sitting on the book, kept so a later crossing tick or cancel can settle it.
     * The original request is retained because settling needs its quantity, side and limit price.
     */
    private record RestingOrder(SendOrderRequest request, String orderId) {
    }
}
