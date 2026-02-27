package com.marmitt.mock.simulator;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.mock.balance.MockBalanceStore;
import com.marmitt.mock.config.MockScenarioConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class OrderValidator {

    private final FeeModel feeModel;

    public OrderValidator(FeeModel feeModel) {
        this.feeModel = feeModel;
    }

    public OrderValidationResult validate(SendOrderRequest request,
                                          MockScenarioConfig config,
                                          MockBalanceStore balanceStore) {
        MockScenarioConfig.ValidationSettings validation = config.validation();
        BigDecimal qty = request.getQuantity();
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            return OrderValidationResult.reject("INVALID_QTY");
        }
        if (validation.minQty().compareTo(BigDecimal.ZERO) > 0
                && qty.compareTo(validation.minQty()) < 0) {
            return OrderValidationResult.reject("MIN_QTY");
        }
        if (validation.stepSize().compareTo(BigDecimal.ZERO) > 0
                && !isMultipleOfStep(qty, validation.stepSize())) {
            return OrderValidationResult.reject("STEP_SIZE");
        }

        BigDecimal price = request.getPrice();
        if (validation.minNotional().compareTo(BigDecimal.ZERO) > 0) {
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                return OrderValidationResult.reject("MISSING_PRICE");
            }
            BigDecimal notional = qty.multiply(price).setScale(8, RoundingMode.HALF_UP);
            if (notional.compareTo(validation.minNotional()) < 0) {
                return OrderValidationResult.reject("MIN_NOTIONAL");
            }
        }

        if (!reserveForRequest(request, config, balanceStore)) {
            return OrderValidationResult.reject("INSUFFICIENT_BALANCE");
        }
        return OrderValidationResult.ok();
    }

    private boolean reserveForRequest(SendOrderRequest request,
                                      MockScenarioConfig config,
                                      MockBalanceStore balanceStore) {
        Symbol symbol = Symbol.of(request.getSymbol());
        String base = symbol.getBaseAsset();
        String quote = symbol.getQuoteAsset();
        BigDecimal qty = request.getQuantity();
        BigDecimal price = request.getPrice();
        if (request.getOrderSide() == com.marmitt.core.enums.OrderSide.BUY) {
            if (price == null) {
                return false;
            }
            BigDecimal estimatedFee = feeModel.calculateFee(qty, price, config);
            BigDecimal required = qty.multiply(price).add(estimatedFee).setScale(8, RoundingMode.HALF_UP);
            return balanceStore.reserve(quote, required);
        }
        return balanceStore.reserve(base, qty);
    }

    private boolean isMultipleOfStep(BigDecimal value, BigDecimal step) {
        if (step.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        BigDecimal remainder = value.remainder(step);
        return remainder.compareTo(BigDecimal.ZERO) == 0;
    }
}
