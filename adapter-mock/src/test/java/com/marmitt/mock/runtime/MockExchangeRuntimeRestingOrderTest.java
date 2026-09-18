package com.marmitt.mock.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.request.SendCancelOrderRequest;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.enums.OrderSide;
import com.marmitt.core.enums.OrderType;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.mock.config.MockMarketDataFeedConfig;
import com.marmitt.mock.config.MockScenarioConfig;
import com.marmitt.mock.simulator.MockMarketDataFeedEngine;
import com.marmitt.mock.simulator.MockOrderExecutionSimulator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Covers the runtime side of resting orders: registering an order that does not cross,
 * filling it when the market moves, and releasing its reservation when it is canceled.
 */
class MockExchangeRuntimeRestingOrderTest {

    private static final String SYMBOL = "BTCUSDT";
    private static final BigDecimal MARKET = new BigDecimal("100.00000000");
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private MockExchangeRuntime runtime;

    @BeforeEach
    void setUp() {
        EventPublisherPort noOpPublisher = new EventPublisherPort() {
            @Override
            public void publishEvent(Object event) {
            }

            @Override
            public void publishEventSync(Object event) {
            }
        };
        ObjectMapper objectMapper = new ObjectMapper();
        MockScenarioConfig config = scenarioConfig();
        MockMarketDataFeedEngine feedEngine = new MockMarketDataFeedEngine(
                noOpPublisher,
                objectMapper,
                new MockMarketDataFeedConfig(false, 1_000L, 10, 4,
                        new BigDecimal("100.00000000"), new BigDecimal("1000.00000000")),
                42L
        );
        runtime = new MockExchangeRuntime(
                noOpPublisher,
                objectMapper,
                new MockOrderExecutionSimulator(),
                config,
                feedEngine
        );
    }

    @Test
    void buyBelowMarketShouldRestInsteadOfFilling() {
        runtime.seedReferencePrice(SYMBOL, MARKET);

        runtime.submitOrderRest(limitBuy(new BigDecimal("90.00000000"), "resting-1"));

        OrderDataDto order = awaitStatus("resting-1", OrderDataDto.OrderStatus.NEW);
        assertEquals(OrderDataDto.OrderStatus.NEW, order.status());
        assertEquals(1, runtime.listOpenOrdersBySymbol(SYMBOL).size(),
                "Ordem que nao cruza deve permanecer aberta");
        assertEquals(0, reserved("USDT").compareTo(new BigDecimal("90.00000000")),
                "Capital fica reservado enquanto a ordem descansa");
    }

    @Test
    void restingBuyShouldFillWhenMarketCrossesItsLimit() {
        runtime.seedReferencePrice(SYMBOL, MARKET);
        runtime.submitOrderRest(limitBuy(new BigDecimal("90.00000000"), "resting-2"));
        awaitStatus("resting-2", OrderDataDto.OrderStatus.NEW);

        runtime.seedReferencePrice(SYMBOL, new BigDecimal("89.00000000"));

        OrderDataDto filled = awaitStatus("resting-2", OrderDataDto.OrderStatus.FILLED);
        assertEquals(0, filled.executedPrice().compareTo(new BigDecimal("90.00000000")),
                "Fill acontece no limite da ordem que descansava");
        assertTrue(runtime.listOpenOrdersBySymbol(SYMBOL).isEmpty(),
                "Ordem preenchida nao pode continuar aberta");
        assertEquals(0, reserved("USDT").compareTo(BigDecimal.ZERO));
        assertEquals(0, available("BTC").compareTo(new BigDecimal("1.00000000")));
    }

    @Test
    void restingBuyShouldNotFillWhileMarketStaysAboveTheLimit() {
        runtime.seedReferencePrice(SYMBOL, MARKET);
        runtime.submitOrderRest(limitBuy(new BigDecimal("90.00000000"), "resting-3"));
        awaitStatus("resting-3", OrderDataDto.OrderStatus.NEW);

        runtime.seedReferencePrice(SYMBOL, new BigDecimal("95.00000000"));
        runtime.seedReferencePrice(SYMBOL, new BigDecimal("90.50000000"));

        OrderDataDto order = order("resting-3").orElseThrow();
        assertEquals(OrderDataDto.OrderStatus.NEW, order.status(),
                "Mercado acima do limite nao pode preencher a BUY");
    }

    @Test
    void cancelingRestingBuyShouldReleaseItsReservation() {
        runtime.seedReferencePrice(SYMBOL, MARKET);
        runtime.submitOrderRest(limitBuy(new BigDecimal("90.00000000"), "resting-4"));
        awaitStatus("resting-4", OrderDataDto.OrderStatus.NEW);

        OrderDataDto canceled = runtime.cancelOrderRest(
                new SendCancelOrderRequest("MOCK", "resting-4", SYMBOL));

        assertEquals(OrderDataDto.OrderStatus.CANCELED, canceled.status());
        assertEquals(0, reserved("USDT").compareTo(BigDecimal.ZERO),
                "Cancelamento devolve a reserva retida");
        assertEquals(0, available("USDT").compareTo(new BigDecimal("1000.00000000")));
    }

    @Test
    void canceledRestingOrderShouldNotFillWhenMarketCrossesLater() {
        runtime.seedReferencePrice(SYMBOL, MARKET);
        runtime.submitOrderRest(limitBuy(new BigDecimal("90.00000000"), "resting-5"));
        awaitStatus("resting-5", OrderDataDto.OrderStatus.NEW);
        runtime.cancelOrderRest(new SendCancelOrderRequest("MOCK", "resting-5", SYMBOL));

        runtime.seedReferencePrice(SYMBOL, new BigDecimal("80.00000000"));

        OrderDataDto order = order("resting-5").orElseThrow();
        assertEquals(OrderDataDto.OrderStatus.CANCELED, order.status(),
                "Ordem cancelada nao pode ser ressuscitada por um tick posterior");
        assertEquals(0, available("USDT").compareTo(new BigDecimal("1000.00000000")));
    }

    @Test
    void marketableBuyShouldFillImmediatelyEvenWithReferencePrice() {
        runtime.seedReferencePrice(SYMBOL, MARKET);

        runtime.submitOrderRest(limitBuy(new BigDecimal("110.00000000"), "marketable-1"));

        OrderDataDto filled = awaitStatus("marketable-1", OrderDataDto.OrderStatus.FILLED);
        assertEquals(OrderDataDto.OrderStatus.FILLED, filled.status());
    }

    @Test
    void withoutReferencePriceOrderShouldFillAsBefore() {
        runtime.submitOrderRest(limitBuy(new BigDecimal("1.00000000"), "no-reference-1"));

        OrderDataDto filled = awaitStatus("no-reference-1", OrderDataDto.OrderStatus.FILLED);
        assertEquals(OrderDataDto.OrderStatus.FILLED, filled.status());
    }

    private SendOrderRequest limitBuy(BigDecimal limitPrice, String clientOrderId) {
        return new SendOrderRequest(
                "MOCK", SYMBOL, new BigDecimal("1.00000000"), limitPrice,
                OrderType.LIMIT, OrderSide.BUY, clientOrderId
        );
    }

    private Optional<OrderDataDto> order(String clientOrderId) {
        return runtime.queryOrderByClientOrderId(SYMBOL, clientOrderId);
    }

    private OrderDataDto awaitStatus(String clientOrderId, OrderDataDto.OrderStatus expected) {
        return await(
                () -> order(clientOrderId).filter(o -> o.status() == expected),
                "status " + expected + " for " + clientOrderId
        );
    }

    private OrderDataDto await(java.util.function.Supplier<Optional<OrderDataDto>> supplier, String what) {
        Instant deadline = Instant.now().plus(TIMEOUT);
        Optional<OrderDataDto> last = Optional.empty();
        while (Instant.now().isBefore(deadline)) {
            last = supplier.get();
            if (last.isPresent()) {
                return last.get();
            }
            sleep();
        }
        fail("Timeout waiting for " + what);
        return null;
    }

    private void sleep() {
        try {
            Thread.sleep(20L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private BigDecimal reserved(String asset) {
        return runtime.queryAccountSnapshot().lockedBalances().getOrDefault(asset, BigDecimal.ZERO);
    }

    private BigDecimal available(String asset) {
        return runtime.queryAccountSnapshot().balances().getOrDefault(asset, BigDecimal.ZERO);
    }

    private static MockScenarioConfig scenarioConfig() {
        return new MockScenarioConfig(
                42L,
                new MockScenarioConfig.Timing(1L, 1L),
                new MockScenarioConfig.Flow(0, null),
                new MockScenarioConfig.Duplicates(0.0, 0),
                new MockScenarioConfig.OutOfOrder(0.0),
                new MockScenarioConfig.Failures(0.0, 0.0),
                new MockScenarioConfig.FeeSettings(MockScenarioConfig.FeeMode.NONE, BigDecimal.ZERO),
                new MockScenarioConfig.SlippageSettings(MockScenarioConfig.SlippageMode.NONE, 0, 0.0, 0),
                new MockScenarioConfig.ValidationSettings(
                        new BigDecimal("0.000001"),
                        new BigDecimal("0.00000001"),
                        new BigDecimal("1.0")
                ),
                new MockScenarioConfig.BalanceSettings(Map.of(
                        "USDT", new BigDecimal("1000.00000000"),
                        "BTC", BigDecimal.ZERO
                ))
        );
    }
}
