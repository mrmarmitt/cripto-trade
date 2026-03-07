package com.marmitt.mock.simulator;

import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.enums.OrderSide;
import com.marmitt.core.enums.OrderType;
import com.marmitt.mock.balance.MockBalanceStore;
import com.marmitt.mock.config.MockOrderScenarioOverride;
import com.marmitt.mock.config.MockScenarioConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MockOrderExecutionSimulatorDeterministicScenarioTest {

    @Test
    void buildScenarioScheduleShouldRespectDeterministicEventPlan() {
        MockOrderExecutionSimulator simulator = new MockOrderExecutionSimulator();
        MockScenarioConfig config = scenarioConfig();
        MockBalanceStore balanceStore = new MockBalanceStore(Map.of(
                "USDT", new BigDecimal("1000.00000000"),
                "BTC", BigDecimal.ZERO
        ));
        SendOrderRequest request = new SendOrderRequest(
                "MOCK",
                "BTCUSDT",
                new BigDecimal("1.00000000"),
                new BigDecimal("100.00000000"),
                OrderType.LIMIT,
                OrderSide.BUY,
                "client-order-1"
        );

        MockOrderScenarioOverride override = new MockOrderScenarioOverride(
                List.of(
                        new MockOrderScenarioOverride.PlannedEvent(
                                OrderDataDto.OrderStatus.PARTIALLY_FILLED,
                                new BigDecimal("0.40000000"),
                                new BigDecimal("101.00000000"),
                                BigDecimal.ZERO,
                                null,
                                120L,
                                1
                        ),
                        new MockOrderScenarioOverride.PlannedEvent(
                                OrderDataDto.OrderStatus.FILLED,
                                new BigDecimal("1.00000000"),
                                new BigDecimal("102.00000000"),
                                BigDecimal.ZERO,
                                null,
                                80L,
                                0
                        )
                ),
                MockOrderScenarioOverride.EventOrdering.AS_IS
        );

        List<MockScheduledOrderEvent> schedule = simulator.buildScenarioSchedule(
                request,
                "MOCK_TEST_1",
                config,
                new Random(42L),
                balanceStore,
                override
        );

        assertEquals(4, schedule.size());

        assertEquals(OrderDataDto.OrderStatus.NEW, schedule.get(0).orderData().status());
        assertEquals(0L, schedule.get(0).delayBeforeMs());

        assertEquals(OrderDataDto.OrderStatus.PARTIALLY_FILLED, schedule.get(1).orderData().status());
        assertEquals(120L, schedule.get(1).delayBeforeMs());
        assertEquals(0, schedule.get(1).orderData().executedQuantity().compareTo(new BigDecimal("0.40000000")));

        assertEquals(OrderDataDto.OrderStatus.PARTIALLY_FILLED, schedule.get(2).orderData().status());
        assertEquals(0L, schedule.get(2).delayBeforeMs());

        assertEquals(OrderDataDto.OrderStatus.FILLED, schedule.get(3).orderData().status());
        assertEquals(80L, schedule.get(3).delayBeforeMs());
        assertEquals(0, schedule.get(3).orderData().executedQuantity().compareTo(new BigDecimal("1.00000000")));
    }

    @Test
    void buildScenarioScheduleShouldApplyReverseOrderingWhenConfigured() {
        MockOrderExecutionSimulator simulator = new MockOrderExecutionSimulator();
        MockScenarioConfig config = scenarioConfig();
        MockBalanceStore balanceStore = new MockBalanceStore(Map.of(
                "USDT", new BigDecimal("1000.00000000"),
                "BTC", BigDecimal.ZERO
        ));
        SendOrderRequest request = new SendOrderRequest(
                "MOCK",
                "BTCUSDT",
                new BigDecimal("1.00000000"),
                new BigDecimal("100.00000000"),
                OrderType.LIMIT,
                OrderSide.BUY,
                "client-order-2"
        );

        MockOrderScenarioOverride override = new MockOrderScenarioOverride(
                List.of(
                        new MockOrderScenarioOverride.PlannedEvent(
                                OrderDataDto.OrderStatus.PARTIALLY_FILLED,
                                new BigDecimal("0.50000000"),
                                null,
                                BigDecimal.ZERO,
                                null,
                                10L,
                                0
                        ),
                        new MockOrderScenarioOverride.PlannedEvent(
                                OrderDataDto.OrderStatus.FILLED,
                                new BigDecimal("1.00000000"),
                                null,
                                BigDecimal.ZERO,
                                null,
                                20L,
                                0
                        )
                ),
                MockOrderScenarioOverride.EventOrdering.REVERSE
        );

        List<MockScheduledOrderEvent> schedule = simulator.buildScenarioSchedule(
                request,
                "MOCK_TEST_2",
                config,
                new Random(42L),
                balanceStore,
                override
        );

        assertEquals(3, schedule.size());
        assertEquals(OrderDataDto.OrderStatus.FILLED, schedule.get(0).orderData().status());
        assertEquals(OrderDataDto.OrderStatus.PARTIALLY_FILLED, schedule.get(1).orderData().status());
        assertEquals(OrderDataDto.OrderStatus.NEW, schedule.get(2).orderData().status());
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
