package com.marmitt.ctrade.domain.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários para PriceAlert entity.
 * Testa lógica de negócio de alertas de preço.
 */
class PriceAlertTest {

    private PriceAlert priceAlert;
    private final String tradingPair = "BTCUSDT";
    private final BigDecimal threshold = new BigDecimal("50000.00");

    @BeforeEach
    void setUp() {
        priceAlert = new PriceAlert(tradingPair, threshold, PriceAlert.AlertType.ABOVE);
    }

    @Test
    void shouldCreatePriceAlertWithCorrectInitialValues() {
        // When
        PriceAlert alert = new PriceAlert("ETHUSDT", new BigDecimal("3000"), PriceAlert.AlertType.BELOW);
        
        // Then
        assertThat(alert.getTradingPair()).isEqualTo("ETHUSDT");
        assertThat(alert.getThreshold()).isEqualByComparingTo("3000");
        assertThat(alert.getAlertType()).isEqualTo(PriceAlert.AlertType.BELOW);
        assertThat(alert.isActive()).isTrue();
        assertThat(alert.getCreatedAt()).isNotNull();
        assertThat(alert.getTriggeredAt()).isNull();
        assertThat(alert.getId()).isNotNull();
        assertThat(alert.getId()).startsWith("ALERT_ETHUSDT_BELOW_");
    }

    @Test
    void shouldGenerateUniqueIds() throws InterruptedException {
        // When
        PriceAlert alert1 = new PriceAlert(tradingPair, threshold, PriceAlert.AlertType.ABOVE);
        Thread.sleep(1); // Ensure different timestamps
        PriceAlert alert2 = new PriceAlert(tradingPair, threshold, PriceAlert.AlertType.ABOVE);
        
        // Then
        assertThat(alert1.getId()).isNotEqualTo(alert2.getId());
        assertThat(alert1.getId()).startsWith("ALERT_BTCUSDT_ABOVE_");
        assertThat(alert2.getId()).startsWith("ALERT_BTCUSDT_ABOVE_");
    }

    @Test
    void shouldTriggerWhenPriceIsAboveThreshold() {
        // Given
        PriceAlert aboveAlert = new PriceAlert(tradingPair, threshold, PriceAlert.AlertType.ABOVE);
        BigDecimal higherPrice = new BigDecimal("55000.00");
        
        // When
        boolean shouldTrigger = aboveAlert.shouldTrigger(higherPrice);
        
        // Then
        assertThat(shouldTrigger).isTrue();
    }

    @Test
    void shouldNotTriggerWhenPriceIsBelowThresholdForAboveAlert() {
        // Given
        PriceAlert aboveAlert = new PriceAlert(tradingPair, threshold, PriceAlert.AlertType.ABOVE);
        BigDecimal lowerPrice = new BigDecimal("45000.00");
        
        // When
        boolean shouldTrigger = aboveAlert.shouldTrigger(lowerPrice);
        
        // Then
        assertThat(shouldTrigger).isFalse();
    }

    @Test
    void shouldNotTriggerWhenPriceEqualsThresholdForAboveAlert() {
        // Given
        PriceAlert aboveAlert = new PriceAlert(tradingPair, threshold, PriceAlert.AlertType.ABOVE);
        
        // When
        boolean shouldTrigger = aboveAlert.shouldTrigger(threshold);
        
        // Then
        assertThat(shouldTrigger).isFalse();
    }

    @Test
    void shouldTriggerWhenPriceIsBelowThreshold() {
        // Given
        PriceAlert belowAlert = new PriceAlert(tradingPair, threshold, PriceAlert.AlertType.BELOW);
        BigDecimal lowerPrice = new BigDecimal("45000.00");
        
        // When
        boolean shouldTrigger = belowAlert.shouldTrigger(lowerPrice);
        
        // Then
        assertThat(shouldTrigger).isTrue();
    }

    @Test
    void shouldNotTriggerWhenPriceIsAboveThresholdForBelowAlert() {
        // Given
        PriceAlert belowAlert = new PriceAlert(tradingPair, threshold, PriceAlert.AlertType.BELOW);
        BigDecimal higherPrice = new BigDecimal("55000.00");
        
        // When
        boolean shouldTrigger = belowAlert.shouldTrigger(higherPrice);
        
        // Then
        assertThat(shouldTrigger).isFalse();
    }

    @Test
    void shouldNotTriggerWhenPriceEqualsThresholdForBelowAlert() {
        // Given
        PriceAlert belowAlert = new PriceAlert(tradingPair, threshold, PriceAlert.AlertType.BELOW);
        
        // When
        boolean shouldTrigger = belowAlert.shouldTrigger(threshold);
        
        // Then
        assertThat(shouldTrigger).isFalse();
    }

    @Test
    void shouldNotTriggerWhenAlertIsInactive() {
        // Given
        priceAlert.setActive(false);
        BigDecimal triggerPrice = new BigDecimal("55000.00");
        
        // When
        boolean shouldTrigger = priceAlert.shouldTrigger(triggerPrice);
        
        // Then
        assertThat(shouldTrigger).isFalse();
    }

    @Test
    void shouldTriggerAlert() {
        // Given
        LocalDateTime beforeTrigger = LocalDateTime.now();
        
        // When
        priceAlert.trigger();
        LocalDateTime afterTrigger = LocalDateTime.now();
        
        // Then
        assertThat(priceAlert.isActive()).isFalse();
        assertThat(priceAlert.getTriggeredAt()).isNotNull();
        assertThat(priceAlert.getTriggeredAt()).isBetween(beforeTrigger, afterTrigger);
    }

    @Test
    void shouldNotTriggerAfterAlreadyTriggered() {
        // Given
        BigDecimal triggerPrice = new BigDecimal("55000.00");
        priceAlert.trigger();
        
        // When
        boolean shouldTrigger = priceAlert.shouldTrigger(triggerPrice);
        
        // Then
        assertThat(shouldTrigger).isFalse();
        assertThat(priceAlert.isActive()).isFalse();
    }

    @Test
    void shouldHandlePrecisionInPriceComparison() {
        // Given
        BigDecimal preciseThreshold = new BigDecimal("50000.123456");
        PriceAlert preciseAlert = new PriceAlert(tradingPair, preciseThreshold, PriceAlert.AlertType.ABOVE);
        
        BigDecimal slightlyHigher = new BigDecimal("50000.123457");
        BigDecimal slightlyLower = new BigDecimal("50000.123455");
        
        // When & Then
        assertThat(preciseAlert.shouldTrigger(slightlyHigher)).isTrue();
        assertThat(preciseAlert.shouldTrigger(slightlyLower)).isFalse();
        assertThat(preciseAlert.shouldTrigger(preciseThreshold)).isFalse();
    }

    @Test
    void shouldWorkWithZeroThreshold() {
        // Given
        BigDecimal zeroThreshold = BigDecimal.ZERO;
        PriceAlert zeroAlert = new PriceAlert(tradingPair, zeroThreshold, PriceAlert.AlertType.ABOVE);
        
        BigDecimal positivePrice = new BigDecimal("0.01");
        BigDecimal negativePrice = new BigDecimal("-0.01");
        
        // When & Then
        assertThat(zeroAlert.shouldTrigger(positivePrice)).isTrue();
        assertThat(zeroAlert.shouldTrigger(negativePrice)).isFalse();
        assertThat(zeroAlert.shouldTrigger(BigDecimal.ZERO)).isFalse();
    }

    @Test
    void shouldWorkWithNegativeThreshold() {
        // Given
        BigDecimal negativeThreshold = new BigDecimal("-100.00");
        PriceAlert negativeAlert = new PriceAlert(tradingPair, negativeThreshold, PriceAlert.AlertType.BELOW);
        
        BigDecimal moreDegative = new BigDecimal("-150.00");
        BigDecimal lessNegative = new BigDecimal("-50.00");
        
        // When & Then
        assertThat(negativeAlert.shouldTrigger(moreDegative)).isTrue();
        assertThat(negativeAlert.shouldTrigger(lessNegative)).isFalse();
    }
}