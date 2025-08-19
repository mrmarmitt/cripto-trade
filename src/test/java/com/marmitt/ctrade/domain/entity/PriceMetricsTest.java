package com.marmitt.ctrade.domain.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários para PriceMetrics entity.
 * Testa cálculos de métricas financeiras e estatísticas de preços.
 */
class PriceMetricsTest {

    private PriceMetrics priceMetrics;
    private final String tradingPair = "BTCUSDT";

    @BeforeEach
    void setUp() {
        priceMetrics = new PriceMetrics(tradingPair);
    }

    @Test
    void shouldCreatePriceMetricsWithCorrectInitialValues() {
        // When
        PriceMetrics metrics = new PriceMetrics("ETHUSDT");
        
        // Then
        assertThat(metrics.getTradingPair()).isEqualTo("ETHUSDT");
        assertThat(metrics.getCurrentPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(metrics.getHighestPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(metrics.getLowestPrice()).isEqualByComparingTo(BigDecimal.valueOf(Double.MAX_VALUE));
        assertThat(metrics.getUpdateCount().get()).isEqualTo(0);
        assertThat(metrics.getFirstUpdateTime()).isNull();
        assertThat(metrics.getLastUpdateTime()).isNull();
    }

    @Test
    void shouldUpdatePriceCorrectlyOnFirstUpdate() {
        // Given
        BigDecimal price = new BigDecimal("50000.00");
        LocalDateTime timestamp = LocalDateTime.now();
        
        // When
        priceMetrics.updatePrice(price, timestamp);
        
        // Then
        assertThat(priceMetrics.getCurrentPrice()).isEqualByComparingTo(price);
        assertThat(priceMetrics.getHighestPrice()).isEqualByComparingTo(price);
        assertThat(priceMetrics.getLowestPrice()).isEqualByComparingTo(price);
        assertThat(priceMetrics.getUpdateCount().get()).isEqualTo(1);
        assertThat(priceMetrics.getFirstUpdateTime()).isEqualTo(timestamp);
        assertThat(priceMetrics.getLastUpdateTime()).isEqualTo(timestamp);
        assertThat(priceMetrics.getHighestPriceTime()).isNull(); // Only set when price changes
        assertThat(priceMetrics.getLowestPriceTime()).isNull(); // Only set when price changes
    }

    @Test
    void shouldUpdateHighestPriceWhenNewPriceIsHigher() {
        // Given
        BigDecimal initialPrice = new BigDecimal("50000.00");
        BigDecimal higherPrice = new BigDecimal("55000.00");
        LocalDateTime time1 = LocalDateTime.now();
        LocalDateTime time2 = time1.plusMinutes(1);
        
        priceMetrics.updatePrice(initialPrice, time1);
        
        // When
        priceMetrics.updatePrice(higherPrice, time2);
        
        // Then
        assertThat(priceMetrics.getCurrentPrice()).isEqualByComparingTo(higherPrice);
        assertThat(priceMetrics.getHighestPrice()).isEqualByComparingTo(higherPrice);
        assertThat(priceMetrics.getLowestPrice()).isEqualByComparingTo(initialPrice);
        assertThat(priceMetrics.getHighestPriceTime()).isEqualTo(time2);
        assertThat(priceMetrics.getLowestPriceTime()).isNull(); // Initial price, no time set
    }

    @Test
    void shouldUpdateLowestPriceWhenNewPriceIsLower() {
        // Given
        BigDecimal initialPrice = new BigDecimal("50000.00");
        BigDecimal lowerPrice = new BigDecimal("45000.00");
        LocalDateTime time1 = LocalDateTime.now();
        LocalDateTime time2 = time1.plusMinutes(1);
        
        priceMetrics.updatePrice(initialPrice, time1);
        
        // When
        priceMetrics.updatePrice(lowerPrice, time2);
        
        // Then
        assertThat(priceMetrics.getCurrentPrice()).isEqualByComparingTo(lowerPrice);
        assertThat(priceMetrics.getHighestPrice()).isEqualByComparingTo(initialPrice);
        assertThat(priceMetrics.getLowestPrice()).isEqualByComparingTo(lowerPrice);
        assertThat(priceMetrics.getHighestPriceTime()).isNull(); // Initial price, no time set
        assertThat(priceMetrics.getLowestPriceTime()).isEqualTo(time2);
    }

    @Test
    void shouldNotUpdateHighestPriceWhenNewPriceIsLowerOrEqual() {
        // Given
        BigDecimal initialPrice = new BigDecimal("50000.00");
        BigDecimal lowerPrice = new BigDecimal("45000.00");
        BigDecimal equalPrice = new BigDecimal("50000.00");
        LocalDateTime time1 = LocalDateTime.now();
        LocalDateTime time2 = time1.plusMinutes(1);
        LocalDateTime time3 = time1.plusMinutes(2);
        
        priceMetrics.updatePrice(initialPrice, time1);
        
        // When
        priceMetrics.updatePrice(lowerPrice, time2);
        priceMetrics.updatePrice(equalPrice, time3);
        
        // Then
        assertThat(priceMetrics.getHighestPrice()).isEqualByComparingTo(initialPrice);
        assertThat(priceMetrics.getHighestPriceTime()).isNull(); // Initial price, no time recorded
    }

    @Test
    void shouldCalculateAveragePriceCorrectly() {
        // Given
        BigDecimal price1 = new BigDecimal("50000.00");
        BigDecimal price2 = new BigDecimal("60000.00");
        BigDecimal price3 = new BigDecimal("40000.00");
        LocalDateTime now = LocalDateTime.now();
        
        // When
        priceMetrics.updatePrice(price1, now);
        priceMetrics.updatePrice(price2, now.plusMinutes(1));
        priceMetrics.updatePrice(price3, now.plusMinutes(2));
        
        // Then
        BigDecimal expectedAverage = new BigDecimal("50000.00000000");
        assertThat(priceMetrics.getAveragePrice()).isEqualByComparingTo(expectedAverage);
    }

    @Test
    void shouldReturnZeroAverageWhenNoUpdates() {
        // When
        BigDecimal average = priceMetrics.getAveragePrice();
        
        // Then
        assertThat(average).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldCalculatePriceRangeCorrectly() {
        // Given
        BigDecimal lowPrice = new BigDecimal("45000.00");
        BigDecimal highPrice = new BigDecimal("55000.00");
        LocalDateTime now = LocalDateTime.now();
        
        priceMetrics.updatePrice(new BigDecimal("50000.00"), now);
        priceMetrics.updatePrice(lowPrice, now.plusMinutes(1));
        priceMetrics.updatePrice(highPrice, now.plusMinutes(2));
        
        // When
        BigDecimal range = priceMetrics.getPriceRange();
        
        // Then
        BigDecimal expectedRange = new BigDecimal("10000.00");
        assertThat(range).isEqualByComparingTo(expectedRange);
    }

    @Test
    void shouldReturnZeroRangeWhenNoUpdates() {
        // When
        BigDecimal range = priceMetrics.getPriceRange();
        
        // Then
        assertThat(range).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldCalculateVolatilityCorrectly() {
        // Given
        BigDecimal lowPrice = new BigDecimal("45000.00");
        BigDecimal highPrice = new BigDecimal("55000.00");
        LocalDateTime now = LocalDateTime.now();
        
        priceMetrics.updatePrice(new BigDecimal("50000.00"), now);
        priceMetrics.updatePrice(lowPrice, now.plusMinutes(1));
        priceMetrics.updatePrice(highPrice, now.plusMinutes(2));
        
        // When
        double volatility = priceMetrics.getVolatility();
        
        // Then
        // Range = 10000, Average = 50000, Volatility = (10000/50000) * 100 = 20%
        assertThat(volatility).isEqualTo(20.0);
    }

    @Test
    void shouldReturnZeroVolatilityWhenNoUpdates() {
        // When
        double volatility = priceMetrics.getVolatility();
        
        // Then
        assertThat(volatility).isEqualTo(0.0);
    }

    @Test
    void shouldReturnZeroVolatilityWhenPricesAreSame() {
        // Given - Same prices result in zero range, hence zero volatility
        BigDecimal samePrice = new BigDecimal("100.00");
        priceMetrics.updatePrice(samePrice, LocalDateTime.now());
        priceMetrics.updatePrice(samePrice, LocalDateTime.now().plusMinutes(1));
        
        // When
        double volatility = priceMetrics.getVolatility();
        
        // Then
        assertThat(volatility).isEqualTo(0.0);
    }

    @Test
    void shouldCalculateUpdateFrequencyPerMinuteCorrectly() {
        // Given
        LocalDateTime startTime = LocalDateTime.now();
        LocalDateTime endTime = startTime.plusMinutes(2);
        
        priceMetrics.updatePrice(new BigDecimal("50000"), startTime);
        priceMetrics.updatePrice(new BigDecimal("51000"), startTime.plusMinutes(1));
        priceMetrics.updatePrice(new BigDecimal("52000"), endTime);
        
        // When
        long frequency = priceMetrics.getUpdateFrequencyPerMinute();
        
        // Then
        // 3 updates in 2 minutes = 1.5 updates per minute (truncated to 1)
        assertThat(frequency).isEqualTo(1);
    }

    @Test
    void shouldReturnUpdateCountWhenTimeSpanIsLessThanOneMinute() {
        // Given
        LocalDateTime startTime = LocalDateTime.now();
        LocalDateTime endTime = startTime.plusSeconds(30);
        
        priceMetrics.updatePrice(new BigDecimal("50000"), startTime);
        priceMetrics.updatePrice(new BigDecimal("51000"), endTime);
        
        // When
        long frequency = priceMetrics.getUpdateFrequencyPerMinute();
        
        // Then
        assertThat(frequency).isEqualTo(2);
    }

    @Test
    void shouldReturnZeroFrequencyWhenNoUpdates() {
        // When
        long frequency = priceMetrics.getUpdateFrequencyPerMinute();
        
        // Then
        assertThat(frequency).isEqualTo(0);
    }

    @Test
    void shouldHandleConcurrentUpdatesCorrectly() {
        // Given
        BigDecimal price1 = new BigDecimal("50000.00");
        BigDecimal price2 = new BigDecimal("51000.00");
        LocalDateTime now = LocalDateTime.now();
        
        // When - Simulate concurrent updates
        priceMetrics.updatePrice(price1, now);
        priceMetrics.updatePrice(price2, now.plusSeconds(1));
        
        // Then
        assertThat(priceMetrics.getUpdateCount().get()).isEqualTo(2);
        assertThat(priceMetrics.getCurrentPrice()).isEqualByComparingTo(price2);
        assertThat(priceMetrics.getHighestPrice()).isEqualByComparingTo(price2);
        assertThat(priceMetrics.getLowestPrice()).isEqualByComparingTo(price1);
    }

    @Test
    void shouldHandlePrecisionInCalculations() {
        // Given
        BigDecimal price1 = new BigDecimal("50000.123456");
        BigDecimal price2 = new BigDecimal("50000.654321");
        LocalDateTime now = LocalDateTime.now();
        
        // When
        priceMetrics.updatePrice(price1, now);
        priceMetrics.updatePrice(price2, now.plusMinutes(1));
        
        // Then
        BigDecimal expectedAverage = new BigDecimal("50000.38888850");
        assertThat(priceMetrics.getAveragePrice()).isEqualByComparingTo(expectedAverage);
        
        BigDecimal expectedRange = new BigDecimal("0.530865");
        assertThat(priceMetrics.getPriceRange()).isEqualByComparingTo(expectedRange);
    }

    @Test
    void shouldHandleZeroPrices() {
        // Given
        BigDecimal zeroPrice = BigDecimal.ZERO;
        BigDecimal positivePrice = new BigDecimal("1000.00");
        LocalDateTime now = LocalDateTime.now();
        
        // When
        priceMetrics.updatePrice(positivePrice, now);
        priceMetrics.updatePrice(zeroPrice, now.plusMinutes(1));
        
        // Then
        assertThat(priceMetrics.getCurrentPrice()).isEqualByComparingTo(zeroPrice);
        assertThat(priceMetrics.getLowestPrice()).isEqualByComparingTo(zeroPrice);
        assertThat(priceMetrics.getHighestPrice()).isEqualByComparingTo(positivePrice);
        assertThat(priceMetrics.getPriceRange()).isEqualByComparingTo(positivePrice);
    }
}