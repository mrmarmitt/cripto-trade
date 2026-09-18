package com.marmitt.mock.simulator;

import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.enums.OrderSide;
import com.marmitt.core.enums.OrderType;
import com.marmitt.mock.balance.MockBalanceStore;
import com.marmitt.mock.config.MockScenarioConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockOrderExecutionSimulatorRestingOrderTest {

    private static final BigDecimal MARKET = new BigDecimal("100.00000000");

    @Test
    void buyBelowMarketShouldRest() {
        MockOrderExecutionSimulator simulator = new MockOrderExecutionSimulator();

        assertTrue(simulator.shouldRest(limitOrder(OrderSide.BUY, new BigDecimal("99.00000000")), MARKET));
    }

    @Test
    void buyAtOrAboveMarketShouldNotRest() {
        MockOrderExecutionSimulator simulator = new MockOrderExecutionSimulator();

        assertFalse(simulator.shouldRest(limitOrder(OrderSide.BUY, MARKET), MARKET),
                "BUY no preco de mercado e marketable");
        assertFalse(simulator.shouldRest(limitOrder(OrderSide.BUY, new BigDecimal("101.00000000")), MARKET));
    }

    @Test
    void sellAboveMarketShouldRest() {
        MockOrderExecutionSimulator simulator = new MockOrderExecutionSimulator();

        assertTrue(simulator.shouldRest(limitOrder(OrderSide.SELL, new BigDecimal("101.00000000")), MARKET));
    }

    @Test
    void sellAtOrBelowMarketShouldNotRest() {
        MockOrderExecutionSimulator simulator = new MockOrderExecutionSimulator();

        assertFalse(simulator.shouldRest(limitOrder(OrderSide.SELL, MARKET), MARKET),
                "SELL no preco de mercado e marketable");
        assertFalse(simulator.shouldRest(limitOrder(OrderSide.SELL, new BigDecimal("99.00000000")), MARKET));
    }

    @Test
    void marketOrderShouldNeverRest() {
        MockOrderExecutionSimulator simulator = new MockOrderExecutionSimulator();
        SendOrderRequest request = new SendOrderRequest(
                "MOCK", "BTCUSDT", new BigDecimal("1.00000000"), new BigDecimal("1.00000000"),
                OrderType.MARKET, OrderSide.BUY, "client-market"
        );

        assertFalse(simulator.shouldRest(request, MARKET));
    }

    @Test
    void withoutReferencePriceShouldNeverRest() {
        MockOrderExecutionSimulator simulator = new MockOrderExecutionSimulator();
        SendOrderRequest farBelowMarket = limitOrder(OrderSide.BUY, new BigDecimal("1.00000000"));

        assertFalse(simulator.shouldRest(farBelowMarket, null),
                "Sem preco de referencia o mock preserva o comportamento always-fill");
        assertFalse(simulator.shouldRest(farBelowMarket, BigDecimal.ZERO));
    }

    @Test
    void restingScheduleShouldStopAtNewAndHoldReservation() {
        MockOrderExecutionSimulator simulator = new MockOrderExecutionSimulator();
        MockScenarioConfig config = scenarioConfig();
        MockBalanceStore balanceStore = new MockBalanceStore(Map.of(
                "USDT", new BigDecimal("1000.00000000"),
                "BTC", BigDecimal.ZERO
        ));
        SendOrderRequest request = limitOrder(OrderSide.BUY, new BigDecimal("99.00000000"));

        List<MockScheduledOrderEvent> schedule =
                simulator.buildRestingSchedule(request, "MOCK_REST_1", config, balanceStore);

        assertEquals(1, schedule.size(), "Ordem que descansa emite apenas NEW");
        assertEquals(OrderDataDto.OrderStatus.NEW, schedule.getFirst().orderData().status());
        assertEquals(0, balanceStore.getReserved("USDT").compareTo(new BigDecimal("99.00000000")),
                "Reserva deve ficar retida enquanto a ordem descansa");
    }

    @Test
    void restingScheduleShouldRejectWhenBalanceIsInsufficient() {
        MockOrderExecutionSimulator simulator = new MockOrderExecutionSimulator();
        MockScenarioConfig config = scenarioConfig();
        MockBalanceStore balanceStore = new MockBalanceStore(Map.of(
                "USDT", new BigDecimal("10.00000000"),
                "BTC", BigDecimal.ZERO
        ));
        SendOrderRequest request = limitOrder(OrderSide.BUY, new BigDecimal("99.00000000"));

        List<MockScheduledOrderEvent> schedule =
                simulator.buildRestingSchedule(request, "MOCK_REST_2", config, balanceStore);

        assertEquals(1, schedule.size());
        assertEquals(OrderDataDto.OrderStatus.REJECTED, schedule.getFirst().orderData().status());
    }

    @Test
    void restingFillShouldSettleAtLimitPriceWithoutReservingAgain() {
        MockOrderExecutionSimulator simulator = new MockOrderExecutionSimulator();
        MockScenarioConfig config = scenarioConfig();
        MockBalanceStore balanceStore = new MockBalanceStore(Map.of(
                "USDT", new BigDecimal("1000.00000000"),
                "BTC", BigDecimal.ZERO
        ));
        SendOrderRequest request = limitOrder(OrderSide.BUY, new BigDecimal("99.00000000"));
        simulator.buildRestingSchedule(request, "MOCK_REST_3", config, balanceStore);

        List<MockScheduledOrderEvent> fill =
                simulator.buildRestingFillSchedule(request, "MOCK_REST_3", config, balanceStore);

        assertEquals(1, fill.size());
        OrderDataDto filled = fill.getFirst().orderData();
        assertEquals(OrderDataDto.OrderStatus.FILLED, filled.status());
        assertEquals(0, filled.executedPrice().compareTo(new BigDecimal("99.00000000")),
                "Ordem que descansava executa no proprio limite");
        assertEquals(0, filled.executedQuantity().compareTo(new BigDecimal("1.00000000")));
        assertEquals(0, balanceStore.getReserved("USDT").compareTo(BigDecimal.ZERO),
                "A reserva retida deve ser consumida pelo fill, nao reservada de novo");
        assertEquals(0, balanceStore.getAvailable("BTC").compareTo(new BigDecimal("1.00000000")));
    }

    @Test
    void releaseReservationForOpenOrderShouldGiveCapitalBack() {
        MockOrderExecutionSimulator simulator = new MockOrderExecutionSimulator();
        MockScenarioConfig config = scenarioConfig();
        MockBalanceStore balanceStore = new MockBalanceStore(Map.of(
                "USDT", new BigDecimal("1000.00000000"),
                "BTC", BigDecimal.ZERO
        ));
        SendOrderRequest request = limitOrder(OrderSide.BUY, new BigDecimal("99.00000000"));
        simulator.buildRestingSchedule(request, "MOCK_REST_4", config, balanceStore);

        simulator.releaseReservationForOpenOrder(request, balanceStore, config);

        assertEquals(0, balanceStore.getReserved("USDT").compareTo(BigDecimal.ZERO));
        assertEquals(0, balanceStore.getAvailable("USDT").compareTo(new BigDecimal("1000.00000000")),
                "Cancelar ordem que descansava restaura o saldo disponivel");
    }

    private static SendOrderRequest limitOrder(OrderSide side, BigDecimal limitPrice) {
        return new SendOrderRequest(
                "MOCK",
                "BTCUSDT",
                new BigDecimal("1.00000000"),
                limitPrice,
                OrderType.LIMIT,
                side,
                "client-" + side + "-" + limitPrice.toPlainString()
        );
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
