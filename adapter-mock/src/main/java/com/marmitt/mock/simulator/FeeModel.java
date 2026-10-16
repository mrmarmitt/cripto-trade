package com.marmitt.mock.simulator;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.enums.OrderSide;
import com.marmitt.mock.config.MockScenarioConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class FeeModel {

    /**
     * Fee estimada em quote asset sobre o valor bruto. Usada para reservar saldo do mock antes da
     * execucao (validacao e liberacao de reserva), independentemente do ativo em que a fee e cobrada.
     */
    public BigDecimal calculateFee(BigDecimal increment, BigDecimal price, MockScenarioConfig config) {
        if (increment == null || price == null) {
            return BigDecimal.ZERO;
        }
        if (increment.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        MockScenarioConfig.FeeSettings feeSettings = config.fees();
        if (feeSettings.mode() == MockScenarioConfig.FeeMode.NONE) {
            return BigDecimal.ZERO;
        }
        if (feeSettings.feeRate().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal gross = increment.multiply(price);
        return gross.multiply(feeSettings.feeRate()).setScale(8, RoundingMode.HALF_UP);
    }

    /**
     * Parte da fee que sai em quote na BUY, para dimensionar a reserva de quote do mock: zero em
     * {@code RECEIVED_ASSET}, onde a BUY paga a fee no ativo base.
     */
    public BigDecimal estimatedBuyQuoteFee(BigDecimal quantity, BigDecimal price, MockScenarioConfig config) {
        if (config.fees().mode() == MockScenarioConfig.FeeMode.RECEIVED_ASSET) {
            return BigDecimal.ZERO;
        }
        return calculateFee(quantity, price, config);
    }

    /**
     * Fee cobrada no fill, expressa no ativo em que e cobrada ({@link #feeAsset}). Em
     * {@code RECEIVED_ASSET} (comportamento da Binance), a BUY paga no ativo base:
     * {@code incremento x taxa}; demais casos pagam em quote sobre o valor bruto (T46).
     */
    public BigDecimal calculateFee(BigDecimal increment, BigDecimal price, OrderSide side, MockScenarioConfig config) {
        if (config.fees().mode() == MockScenarioConfig.FeeMode.RECEIVED_ASSET && side == OrderSide.BUY) {
            if (increment == null || increment.compareTo(BigDecimal.ZERO) <= 0
                    || config.fees().feeRate().compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }
            return increment.multiply(config.fees().feeRate()).setScale(8, RoundingMode.HALF_UP);
        }
        return calculateFee(increment, price, config);
    }

    /**
     * Ativo em que a fee do fill e cobrada: base na BUY e quote na SELL em {@code RECEIVED_ASSET};
     * quote nos dois lados em {@code SAME_CURRENCY}; {@code null} sem fee.
     */
    public String feeAsset(OrderSide side, String symbol, MockScenarioConfig config) {
        Symbol pair = Symbol.of(symbol);
        return switch (config.fees().mode()) {
            case NONE -> null;
            case SAME_CURRENCY -> pair.getQuoteAsset();
            case RECEIVED_ASSET -> side == OrderSide.BUY ? pair.getBaseAsset() : pair.getQuoteAsset();
        };
    }
}
