package com.marmitt.core.domain;

import com.marmitt.core.enums.RiskLevel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RiskAssessment(
        String requestId,
        Symbol symbol,
        RiskLevel riskLevel,
        BigDecimal riskScore,
        boolean tradeAllowed,
        BigDecimal maxAllowedQuantity,
        BigDecimal recommendedQuantity,
        List<String> warnings,
        List<String> violations,
        Instant assessmentTime
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static RiskAssessment allowed(String requestId, Symbol symbol, BigDecimal maxQuantity) {
        return new RiskAssessment(
                requestId,
                symbol,
                RiskLevel.LOW,
                BigDecimal.valueOf(0.2),
                true,
                maxQuantity,
                maxQuantity,
                List.of(),
                List.of(),
                Instant.now()
        );
    }
    
    public static RiskAssessment denied(String requestId, Symbol symbol, List<String> violations) {
        return new RiskAssessment(
                requestId,
                symbol,
                RiskLevel.HIGH,
                BigDecimal.valueOf(0.9),
                false,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(),
                violations,
                Instant.now()
        );
    }
    
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
    
    public boolean hasViolations() {
        return !violations.isEmpty();
    }
    
    public boolean isHighRisk() {
        return RiskLevel.HIGH.equals(riskLevel) || RiskLevel.CRITICAL.equals(riskLevel);
    }
    
    public boolean isLowRisk() {
        return RiskLevel.LOW.equals(riskLevel);
    }

    public static class Builder {
        private String requestId;
        private Symbol symbol;
        private RiskLevel riskLevel = RiskLevel.MEDIUM;
        private BigDecimal riskScore = BigDecimal.valueOf(0.5);
        private boolean tradeAllowed = false;
        private BigDecimal maxAllowedQuantity = BigDecimal.ZERO;
        private BigDecimal recommendedQuantity = BigDecimal.ZERO;
        private List<String> warnings = List.of();
        private List<String> violations = List.of();
        private Instant assessmentTime = Instant.now();

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder symbol(Symbol symbol) {
            this.symbol = symbol;
            return this;
        }

        public Builder riskLevel(RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
            return this;
        }

        public Builder riskScore(BigDecimal riskScore) {
            this.riskScore = riskScore;
            return this;
        }

        public Builder tradeAllowed(boolean tradeAllowed) {
            this.tradeAllowed = tradeAllowed;
            return this;
        }

        public Builder maxAllowedQuantity(BigDecimal maxAllowedQuantity) {
            this.maxAllowedQuantity = maxAllowedQuantity;
            return this;
        }

        public Builder recommendedQuantity(BigDecimal recommendedQuantity) {
            this.recommendedQuantity = recommendedQuantity;
            return this;
        }

        public Builder warnings(List<String> warnings) {
            this.warnings = warnings;
            return this;
        }

        public Builder violations(List<String> violations) {
            this.violations = violations;
            return this;
        }

        public Builder assessmentTime(Instant assessmentTime) {
            this.assessmentTime = assessmentTime;
            return this;
        }

        public RiskAssessment build() {
            return new RiskAssessment(requestId, symbol, riskLevel, riskScore, tradeAllowed,
                                    maxAllowedQuantity, recommendedQuantity, warnings, violations, assessmentTime);
        }
    }
}