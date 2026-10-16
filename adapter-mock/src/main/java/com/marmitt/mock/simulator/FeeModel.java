package com.marmitt.mock.simulator;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.enums.OrderSide;
import com.marmitt.mock.config.MockScenarioConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Fee do mock no padrao da Binance Spot (T46): cobrada no ativo recebido, base na BUY e quote na
 * SELL. Como a BUY nao paga fee em quote, a reserva de quote do mock e so o valor bruto.
 */
public class FeeModel {

    /**
     * Fee cobrada no fill, expressa no ativo em que e cobrada ({@link #feeAsset}): na BUY
     * {@code incremento x taxa} (base); na SELL {@code incremento x preco x taxa} (quote).
     */
    public BigDecimal calculateFee(BigDecimal increment, BigDecimal price, OrderSide side, MockScenarioConfig config) {
        MockScenarioConfig.FeeSettings feeSettings = config.fees();
        if (feeSettings.mode() == MockScenarioConfig.FeeMode.NONE
                || feeSettings.feeRate().signum() == 0
                || increment == null || increment.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal charged = side == OrderSide.BUY ? increment : grossOf(increment, price);
        return charged.multiply(feeSettings.feeRate()).setScale(8, RoundingMode.HALF_UP);
    }

    /**
     * Ativo em que a fee do fill e cobrada: base na BUY, quote na SELL; {@code null} sem fee.
     */
    public String feeAsset(OrderSide side, String symbol, MockScenarioConfig config) {
        if (config.fees().mode() == MockScenarioConfig.FeeMode.NONE) {
            return null;
        }
        Symbol pair = Symbol.of(symbol);
        return side == OrderSide.BUY ? pair.getBaseAsset() : pair.getQuoteAsset();
    }

    private static BigDecimal grossOf(BigDecimal increment, BigDecimal price) {
        return price == null ? BigDecimal.ZERO : increment.multiply(price);
    }
}
