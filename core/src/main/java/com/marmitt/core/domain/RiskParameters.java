package com.marmitt.core.domain;

import java.math.BigDecimal;

public record RiskParameters(
        BigDecimal maxPositionSize,
        BigDecimal maxAccountRisk,
        BigDecimal maxDrawdown,
        BigDecimal stopLossPercentage,
        BigDecimal takeProfitPercentage,
        int maxOpenPositions,
        BigDecimal maxDailyLoss,
        BigDecimal minAccountBalance,
        boolean enableRiskLimits
) {
    public static RiskParameters defaultParameters() {
        return new RiskParameters(
                BigDecimal.valueOf(1000),      // Max position: $1000
                BigDecimal.valueOf(0.02),      // Max account risk: 2%
                BigDecimal.valueOf(0.10),      // Max drawdown: 10%
                BigDecimal.valueOf(0.05),      // Stop loss: 5%
                BigDecimal.valueOf(0.10),      // Take profit: 10%
                5,                             // Max 5 open positions
                BigDecimal.valueOf(500),       // Max daily loss: $500
                BigDecimal.valueOf(100),       // Min account balance: $100
                true                           // Risk limits enabled
        );
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public boolean isWithinLimits(BigDecimal positionSize, BigDecimal accountBalance, int openPositions) {
        if (!enableRiskLimits) return true;
        
        return positionSize.compareTo(maxPositionSize) <= 0 &&
               accountBalance.compareTo(minAccountBalance) >= 0 &&
               openPositions <= maxOpenPositions;
    }
    
    public BigDecimal calculateMaxAllowedPosition(BigDecimal accountBalance) {
        if (!enableRiskLimits) return maxPositionSize;
        
        BigDecimal riskBasedMax = accountBalance.multiply(maxAccountRisk);
        return riskBasedMax.min(maxPositionSize);
    }

    public static class Builder {
        private BigDecimal maxPositionSize = BigDecimal.valueOf(1000);
        private BigDecimal maxAccountRisk = BigDecimal.valueOf(0.02);
        private BigDecimal maxDrawdown = BigDecimal.valueOf(0.10);
        private BigDecimal stopLossPercentage = BigDecimal.valueOf(0.05);
        private BigDecimal takeProfitPercentage = BigDecimal.valueOf(0.10);
        private int maxOpenPositions = 5;
        private BigDecimal maxDailyLoss = BigDecimal.valueOf(500);
        private BigDecimal minAccountBalance = BigDecimal.valueOf(100);
        private boolean enableRiskLimits = true;

        public Builder maxPositionSize(BigDecimal maxPositionSize) {
            this.maxPositionSize = maxPositionSize;
            return this;
        }

        public Builder maxAccountRisk(BigDecimal maxAccountRisk) {
            this.maxAccountRisk = maxAccountRisk;
            return this;
        }

        public Builder maxDrawdown(BigDecimal maxDrawdown) {
            this.maxDrawdown = maxDrawdown;
            return this;
        }

        public Builder stopLossPercentage(BigDecimal stopLossPercentage) {
            this.stopLossPercentage = stopLossPercentage;
            return this;
        }

        public Builder takeProfitPercentage(BigDecimal takeProfitPercentage) {
            this.takeProfitPercentage = takeProfitPercentage;
            return this;
        }

        public Builder maxOpenPositions(int maxOpenPositions) {
            this.maxOpenPositions = maxOpenPositions;
            return this;
        }

        public Builder maxDailyLoss(BigDecimal maxDailyLoss) {
            this.maxDailyLoss = maxDailyLoss;
            return this;
        }

        public Builder minAccountBalance(BigDecimal minAccountBalance) {
            this.minAccountBalance = minAccountBalance;
            return this;
        }

        public Builder enableRiskLimits(boolean enableRiskLimits) {
            this.enableRiskLimits = enableRiskLimits;
            return this;
        }

        public RiskParameters build() {
            return new RiskParameters(maxPositionSize, maxAccountRisk, maxDrawdown,
                                    stopLossPercentage, takeProfitPercentage, maxOpenPositions,
                                    maxDailyLoss, minAccountBalance, enableRiskLimits);
        }
    }
}