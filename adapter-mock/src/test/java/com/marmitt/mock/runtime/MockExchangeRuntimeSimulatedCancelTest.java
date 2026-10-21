package com.marmitt.mock.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.request.SendCancelOrderRequest;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.enums.OrderSide;
import com.marmitt.core.enums.OrderType;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.mock.config.MockMarketDataFeedConfig;
import com.marmitt.mock.config.MockOrderScenarioOverride;
import com.marmitt.mock.config.MockOrderScenarioOverride.PlannedEvent;
import com.marmitt.mock.config.MockScenarioConfig;
import com.marmitt.mock.simulator.MockMarketDataFeedEngine;
import com.marmitt.mock.simulator.MockOrderExecutionSimulator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * T52: cancelar uma ordem do caminho simulado (MARKET ou LIMIT marketable) enquanto o cronograma ainda
 * publica. Quem fecha a ordem primeiro vence, nada sai depois do terminal e o saldo fica debitado so
 * pelo que foi executado. Como no teste das ordens que descansam, o estado observavel e o snapshot da
 * consulta REST e os saldos da conta do mock.
 */
class MockExchangeRuntimeSimulatedCancelTest {

    private static final String SYMBOL = "BTCUSDT";
    private static final BigDecimal PRICE = new BigDecimal("100.00000000");
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    /** Folga para o cronograma terminar de publicar, se fosse continuar. */
    private static final long AFTER_SCHEDULE_MS = 900L;

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
        MockMarketDataFeedEngine feedEngine = new MockMarketDataFeedEngine(
                noOpPublisher,
                objectMapper,
                new MockMarketDataFeedConfig(false, 1_000L, 10, 4,
                        new BigDecimal("100.00000000"), new BigDecimal("1000.00000000")),
                42L
        );
        runtime = new MockExchangeRuntime(noOpPublisher, objectMapper, new MockOrderExecutionSimulator(),
                scenarioConfig(), feedEngine);
    }

    @Test
    void cancelBeforeTheFirstFillPublishesOnlyCanceledAndReturnsTheWholeReservation() {
        plan("before-fill", MockOrderScenarioOverride.EventOrdering.AS_IS,
                partial("0.40000000", 300L, 0), filled(300L));
        runtime.submitOrderRest(buy("before-fill"));

        OrderDataDto canceled = cancel("before-fill");

        assertEquals(OrderDataDto.OrderStatus.CANCELED, canceled.status());
        assertEquals(0, canceled.executedQuantity().signum());
        sleep(AFTER_SCHEDULE_MS);
        assertEquals(OrderDataDto.OrderStatus.CANCELED, order("before-fill").status(),
                "Nenhum fill do cronograma pode sair depois do CANCELED");
        assertBalances("1000.00000000", "0", "0");
    }

    @Test
    void cancelBetweenPartialsKeepsWhatExecutedAndGivesBackTheRest() {
        plan("between-partials", MockOrderScenarioOverride.EventOrdering.AS_IS,
                partial("0.40000000", 0L, 0), filled(500L));
        runtime.submitOrderRest(buy("between-partials"));
        awaitStatus("between-partials", OrderDataDto.OrderStatus.PARTIALLY_FILLED);

        OrderDataDto canceled = cancel("between-partials");

        assertEquals(OrderDataDto.OrderStatus.CANCELED, canceled.status());
        assertEquals(0, canceled.executedQuantity().compareTo(new BigDecimal("0.40000000")));
        sleep(AFTER_SCHEDULE_MS);
        OrderDataDto afterwards = order("between-partials");
        assertEquals(OrderDataDto.OrderStatus.CANCELED, afterwards.status(), "O FILLED planejado nao sai");
        assertEquals(0, afterwards.executedQuantity().compareTo(new BigDecimal("0.40000000")));
        // 0.4 BTC pagos a 100; os 60 USDT reservados para o restante voltam.
        assertBalances("960.00000000", "0", "0.40000000");
    }

    @Test
    void cancelAfterTheFillLosesAndReturnsTheFill() {
        runtime.submitOrderRest(buy("after-fill"));
        awaitStatus("after-fill", OrderDataDto.OrderStatus.FILLED);

        OrderDataDto result = cancel("after-fill");

        assertEquals(OrderDataDto.OrderStatus.FILLED, result.status(),
                "Cancelamento que perde a corrida devolve o desfecho vencedor");
        assertEquals(OrderDataDto.OrderStatus.FILLED, order("after-fill").status());
        assertBalances("900.00000000", "0", "1.00000000");
    }

    @Test
    void outOfOrderEventsAreStillPublishedButTheQueryNeverLeavesTheTerminal() {
        // REVERSE: NEW, FILLED, PARTIALLY_FILLED. O parcial atrasado e entregue (o core precisa ser
        // idempotente), mas a consulta REST continua respondendo FILLED.
        plan("reverse", MockOrderScenarioOverride.EventOrdering.REVERSE,
                partial("0.40000000", 0L, 0), filled(0L));
        runtime.submitOrderRest(buy("reverse"));
        awaitStatus("reverse", OrderDataDto.OrderStatus.FILLED);

        sleep(300L);

        assertEquals(OrderDataDto.OrderStatus.FILLED, order("reverse").status());
        assertBalances("900.00000000", "0", "1.00000000");
    }

    @Test
    void duplicatedEventsSettleTheBalanceOnce() {
        plan("duplicates", MockOrderScenarioOverride.EventOrdering.AS_IS,
                partial("0.40000000", 0L, 2), filled(0L));
        runtime.submitOrderRest(buy("duplicates"));
        awaitStatus("duplicates", OrderDataDto.OrderStatus.FILLED);

        assertBalances("900.00000000", "0", "1.00000000");
    }

    // ---------------------------------------------------------------- helpers

    private void plan(String clientOrderId, MockOrderScenarioOverride.EventOrdering ordering, PlannedEvent... events) {
        runtime.registerOrderScenarioOverride(clientOrderId, new MockOrderScenarioOverride(List.of(events), ordering));
    }

    private static PlannedEvent partial(String executed, long delayMs, int duplicates) {
        return new PlannedEvent(OrderDataDto.OrderStatus.PARTIALLY_FILLED, new BigDecimal(executed), PRICE,
                BigDecimal.ZERO, null, delayMs, duplicates);
    }

    private static PlannedEvent filled(long delayMs) {
        return new PlannedEvent(OrderDataDto.OrderStatus.FILLED, null, PRICE, BigDecimal.ZERO, null, delayMs, 0);
    }

    /** LIMIT sem preco de referencia no mock: executa pelo caminho simulado, como uma marketable. */
    private static SendOrderRequest buy(String clientOrderId) {
        return new SendOrderRequest("MOCK", SYMBOL, new BigDecimal("1.00000000"), PRICE,
                OrderType.LIMIT, OrderSide.BUY, clientOrderId);
    }

    private OrderDataDto cancel(String clientOrderId) {
        return runtime.cancelOrderRest(new SendCancelOrderRequest("MOCK", clientOrderId, SYMBOL));
    }

    private OrderDataDto order(String clientOrderId) {
        return runtime.queryOrderByClientOrderId(SYMBOL, clientOrderId).orElseThrow();
    }

    private void awaitStatus(String clientOrderId, OrderDataDto.OrderStatus expected) {
        Instant deadline = Instant.now().plus(TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            Optional<OrderDataDto> current = runtime.queryOrderByClientOrderId(SYMBOL, clientOrderId);
            if (current.isPresent() && current.get().status() == expected) {
                return;
            }
            sleep(10L);
        }
        fail("Timeout waiting for " + expected + " of " + clientOrderId);
    }

    private void assertBalances(String usdtAvailable, String usdtReserved, String btcAvailable) {
        Map<String, BigDecimal> available = runtime.queryAccountSnapshot().balances();
        Map<String, BigDecimal> reserved = runtime.queryAccountSnapshot().lockedBalances();
        assertEquals(0, available.get("USDT").compareTo(new BigDecimal(usdtAvailable)), "USDT disponivel " + available);
        assertEquals(0, reserved.get("USDT").compareTo(new BigDecimal(usdtReserved)), "USDT reservado " + reserved);
        assertEquals(0, available.get("BTC").compareTo(new BigDecimal(btcAvailable)), "BTC disponivel " + available);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
