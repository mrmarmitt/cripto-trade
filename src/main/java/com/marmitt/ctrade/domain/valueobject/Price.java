package com.marmitt.ctrade.domain.valueobject;

import lombok.Value;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Value
public class Price {
    BigDecimal value;

    public Price(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("Price value cannot be null");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
        this.value = value.setScale(8, RoundingMode.HALF_UP);
    }

    public Price(String value) {
        this(new BigDecimal(value));
    }

    public Price(double value) {
        this(BigDecimal.valueOf(value));
    }

    public Price add(Price other) {
        return new Price(this.value.add(other.value));
    }

    public Price subtract(Price other) {
        return new Price(this.value.subtract(other.value));
    }

    public Price multiply(BigDecimal multiplier) {
        return new Price(this.value.multiply(multiplier));
    }

    public Price divide(BigDecimal divisor) {
        return new Price(this.value.divide(divisor, RoundingMode.HALF_UP));
    }

    public boolean isGreaterThan(Price other) {
        return this.value.compareTo(other.value) > 0;
    }

    public boolean isLessThan(Price other) {
        return this.value.compareTo(other.value) < 0;
    }

    public boolean isEqualTo(Price other) {
        return this.value.compareTo(other.value) == 0;
    }
}