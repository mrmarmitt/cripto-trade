package com.marmitt.mock.simulator;

import com.marmitt.mock.config.MockScenarioConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class FeeModel {

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
}
