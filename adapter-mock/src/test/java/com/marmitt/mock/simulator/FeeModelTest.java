package com.marmitt.mock.simulator;

import com.marmitt.core.enums.OrderSide;
import com.marmitt.mock.config.MockScenarioConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * T46: o modo RECEIVED_ASSET simula a Binance Spot — fee no ativo recebido.
 */
class FeeModelTest {

    private static final BigDecimal QTY = new BigDecimal("0.001");
    private static final BigDecimal PRICE = new BigDecimal("66300");

    private final FeeModel feeModel = new FeeModel();

    @Test
    void receivedAssetChargesBuyInBaseAndSellInQuote() {
        MockScenarioConfig config = config(MockScenarioConfig.FeeMode.RECEIVED_ASSET);

        assertAmount("0.00000100", feeModel.calculateFee(QTY, PRICE, OrderSide.BUY, config));
        assertEquals("BTC", feeModel.feeAsset(OrderSide.BUY, "BTCUSDT", config));
        assertAmount("0.06630000", feeModel.calculateFee(QTY, PRICE, OrderSide.SELL, config));
        assertEquals("USDT", feeModel.feeAsset(OrderSide.SELL, "BTCUSDT", config));
    }

    @Test
    void noneHasNoFeeAsset() {
        MockScenarioConfig config = config(MockScenarioConfig.FeeMode.NONE);

        assertAmount("0", feeModel.calculateFee(QTY, PRICE, OrderSide.BUY, config));
        assertNull(feeModel.feeAsset(OrderSide.BUY, "BTCUSDT", config));
    }

    private static MockScenarioConfig config(MockScenarioConfig.FeeMode mode) {
        return new MockScenarioConfig(
                42L,
                new MockScenarioConfig.Timing(1L, 1L),
                new MockScenarioConfig.Flow(0, null),
                new MockScenarioConfig.Duplicates(0.0, 0),
                new MockScenarioConfig.OutOfOrder(0.0),
                new MockScenarioConfig.Failures(0.0, 0.0),
                new MockScenarioConfig.FeeSettings(mode, new BigDecimal("0.0010")),
                new MockScenarioConfig.SlippageSettings(MockScenarioConfig.SlippageMode.NONE, 0, 0.0, 0),
                new MockScenarioConfig.ValidationSettings(
                        new BigDecimal("0.000001"), new BigDecimal("0.00000001"), new BigDecimal("1.0")),
                new MockScenarioConfig.BalanceSettings(Map.of("USDT", new BigDecimal("1000"), "BTC", BigDecimal.ZERO))
        );
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }
}
