package com.marmitt.ctrade.domain.valueobject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.*;

class PriceTest {

    @Test
    @DisplayName("Should create price with BigDecimal value")
    void shouldCreatePriceWithBigDecimalValue() {
        BigDecimal value = new BigDecimal("100.50");
        Price price = new Price(value);
        
        assertThat(price.getValue()).isEqualTo(value.setScale(8, RoundingMode.HALF_UP));
    }

    @Test
    @DisplayName("Should create price with string value")
    void shouldCreatePriceWithStringValue() {
        Price price = new Price("100.50");
        
        assertThat(price.getValue()).isEqualByComparingTo(new BigDecimal("100.50000000"));
    }

    @Test
    @DisplayName("Should create price with double value")
    void shouldCreatePriceWithDoubleValue() {
        Price price = new Price(100.50);
        
        assertThat(price.getValue()).isEqualByComparingTo(new BigDecimal("100.50000000"));
    }

    @Test
    @DisplayName("Should set scale to 8 decimal places")
    void shouldSetScaleTo8DecimalPlaces() {
        Price price = new Price("100.123456789");
        
        assertThat(price.getValue().scale()).isEqualTo(8);
        assertThat(price.getValue()).isEqualByComparingTo(new BigDecimal("100.12345679"));
    }

    @Test
    @DisplayName("Should throw exception for null value")
    void shouldThrowExceptionForNullValue() {
        assertThatThrownBy(() -> new Price((BigDecimal) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Price value cannot be null");
    }

    @Test
    @DisplayName("Should throw exception for negative value")
    void shouldThrowExceptionForNegativeValue() {
        assertThatThrownBy(() -> new Price("-10.50"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Price cannot be negative");
    }

    @Test
    @DisplayName("Should allow zero value")
    void shouldAllowZeroValue() {
        Price price = new Price("0");
        
        assertThat(price.getValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should add prices correctly")
    void shouldAddPricesCorrectly() {
        Price price1 = new Price("100.50");
        Price price2 = new Price("50.25");
        
        Price result = price1.add(price2);
        
        assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("150.75"));
    }

    @Test
    @DisplayName("Should subtract prices correctly")
    void shouldSubtractPricesCorrectly() {
        Price price1 = new Price("100.50");
        Price price2 = new Price("50.25");
        
        Price result = price1.subtract(price2);
        
        assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("50.25"));
    }

    @Test
    @DisplayName("Should multiply price correctly")
    void shouldMultiplyPriceCorrectly() {
        Price price = new Price("100.50");
        BigDecimal multiplier = new BigDecimal("2");
        
        Price result = price.multiply(multiplier);
        
        assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("201.00"));
    }

    @Test
    @DisplayName("Should divide price correctly")
    void shouldDividePriceCorrectly() {
        Price price = new Price("100.50");
        BigDecimal divisor = new BigDecimal("2");
        
        Price result = price.divide(divisor);
        
        assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("50.25"));
    }

    @Test
    @DisplayName("Should compare prices correctly - greater than")
    void shouldComparePricesCorrectlyGreaterThan() {
        Price price1 = new Price("100.50");
        Price price2 = new Price("50.25");
        
        assertThat(price1.isGreaterThan(price2)).isTrue();
        assertThat(price2.isGreaterThan(price1)).isFalse();
    }

    @Test
    @DisplayName("Should compare prices correctly - less than")
    void shouldComparePricesCorrectlyLessThan() {
        Price price1 = new Price("50.25");
        Price price2 = new Price("100.50");
        
        assertThat(price1.isLessThan(price2)).isTrue();
        assertThat(price2.isLessThan(price1)).isFalse();
    }

    @Test
    @DisplayName("Should compare prices correctly - equal to")
    void shouldComparePricesCorrectlyEqualTo() {
        Price price1 = new Price("100.50");
        Price price2 = new Price("100.50");
        Price price3 = new Price("100.49");
        
        assertThat(price1.isEqualTo(price2)).isTrue();
        assertThat(price1.isEqualTo(price3)).isFalse();
    }

    @Test
    @DisplayName("Should be immutable value object")
    void shouldBeImmutableValueObject() {
        Price price1 = new Price("100.50");
        Price price2 = new Price("100.50");
        
        assertThat(price1).isEqualTo(price2);
        assertThat(price1.hashCode()).isEqualTo(price2.hashCode());
    }
}