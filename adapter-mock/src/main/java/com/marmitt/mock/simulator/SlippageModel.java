package com.marmitt.mock.simulator;

import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.mock.config.MockScenarioConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

public class SlippageModel {

    public BigDecimal calculateBaseExecutedPrice(SendOrderRequest request,
                                                 MockScenarioConfig config,
                                                 Random random) {
        if (request.getPrice() == null) {
            return BigDecimal.ZERO;
        }
        MockScenarioConfig.SlippageSettings slippage = config.slippage();
        if (slippage.mode() == MockScenarioConfig.SlippageMode.NONE) {
            return request.getPrice();
        }
        if (slippage.mode() == MockScenarioConfig.SlippageMode.MARKET_ONLY
                && request.getOrderType() != com.marmitt.core.enums.OrderType.MARKET) {
            return request.getPrice();
        }
        int maxBps = slippage.maxSlippageBps();
        if (maxBps <= 0) {
            return request.getPrice();
        }
        int stepBps = Math.max(1, slippage.priceStepBps());
        int steps = maxBps / stepBps;
        if (steps <= 0) {
            return request.getPrice();
        }
        int stepCount = 1 + random.nextInt(steps);
        int bps = stepCount * stepBps;
        boolean improve = random.nextDouble() < slippage.priceImprovementChance();
        int signedBps = improve ? -bps : bps;

        BigDecimal multiplier = BigDecimal.valueOf(10000L + signedBps)
                .divide(BigDecimal.valueOf(10000L), 8, RoundingMode.HALF_UP);

        BigDecimal executed = request.getPrice().multiply(multiplier).setScale(8, RoundingMode.HALF_UP);
        if (executed.compareTo(BigDecimal.ZERO) <= 0) {
            return request.getPrice();
        }
        return applyLimitConstraint(request, executed);
    }

    public BigDecimal calculateEventExecutedPrice(SendOrderRequest request,
                                                  BigDecimal basePrice,
                                                  MockScenarioConfig config,
                                                  Random random) {
        if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) <= 0) {
            return calculateBaseExecutedPrice(request, config, random);
        }
        MockScenarioConfig.SlippageSettings slippage = config.slippage();
        if (slippage.mode() == MockScenarioConfig.SlippageMode.NONE) {
            return basePrice;
        }
        int maxBps = slippage.maxSlippageBps();
        if (maxBps <= 0) {
            return basePrice;
        }
        int stepBps = Math.max(1, slippage.priceStepBps());
        int steps = Math.max(1, maxBps / stepBps);
        int stepCount = random.nextInt(steps + 1);
        int bps = stepCount * stepBps;
        boolean improve = random.nextDouble() < slippage.priceImprovementChance();
        int signedBps = improve ? -bps : bps;

        BigDecimal multiplier = BigDecimal.valueOf(10000L + signedBps)
                .divide(BigDecimal.valueOf(10000L), 8, RoundingMode.HALF_UP);
        BigDecimal executed = basePrice.multiply(multiplier).setScale(8, RoundingMode.HALF_UP);
        if (executed.compareTo(BigDecimal.ZERO) <= 0) {
            return basePrice;
        }
        return applyLimitConstraint(request, executed);
    }

    private BigDecimal applyLimitConstraint(SendOrderRequest request, BigDecimal executedPrice) {
        if (request.getOrderType() != com.marmitt.core.enums.OrderType.LIMIT) {
            return executedPrice;
        }
        BigDecimal limitPrice = request.getPrice();
        if (limitPrice == null) {
            return executedPrice;
        }
        if (request.getOrderSide() == com.marmitt.core.enums.OrderSide.BUY
                && executedPrice.compareTo(limitPrice) > 0) {
            return limitPrice;
        }
        if (request.getOrderSide() == com.marmitt.core.enums.OrderSide.SELL
                && executedPrice.compareTo(limitPrice) < 0) {
            return limitPrice;
        }
        return executedPrice;
    }
}
