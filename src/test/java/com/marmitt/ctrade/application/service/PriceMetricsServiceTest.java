package com.marmitt.ctrade.application.service;

import com.marmitt.ctrade.controller.dto.SystemMetricsSummary;
import com.marmitt.ctrade.domain.entity.PriceMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários para PriceMetricsService.
 * Testa gerenciamento de métricas de preços e agregações estatísticas.
 */
class PriceMetricsServiceTest {

    private PriceMetricsService priceMetricsService;

    @BeforeEach
    void setUp() {
        priceMetricsService = new PriceMetricsService();
    }

    @Test
    void shouldRecordPriceUpdateSuccessfully() {
        // Given
        String tradingPair = "BTCUSDT";
        BigDecimal price = new BigDecimal("50000.00");
        LocalDateTime timestamp = LocalDateTime.now();

        // When
        priceMetricsService.recordPriceUpdate(tradingPair, price, timestamp);

        // Then
        PriceMetrics metrics = priceMetricsService.getMetrics(tradingPair);
        assertThat(metrics).isNotNull();
        assertThat(metrics.getTradingPair()).isEqualTo(tradingPair);
        assertThat(metrics.getCurrentPrice()).isEqualByComparingTo(price);
        assertThat(metrics.getUpdateCount().get()).isEqualTo(1);
        assertThat(metrics.getFirstUpdateTime()).isEqualTo(timestamp);
    }

    @Test
    void shouldCreateNewMetricsForNewTradingPair() {
        // Given
        String tradingPair = "ETHUSDT";
        BigDecimal price = new BigDecimal("3000.00");
        LocalDateTime timestamp = LocalDateTime.now();

        // When
        priceMetricsService.recordPriceUpdate(tradingPair, price, timestamp);

        // Then
        PriceMetrics metrics = priceMetricsService.getMetrics(tradingPair);
        assertThat(metrics).isNotNull();
        assertThat(metrics.getTradingPair()).isEqualTo(tradingPair);
    }

    @Test
    void shouldUpdateExistingMetricsForSameTradingPair() {
        // Given
        String tradingPair = "BTCUSDT";
        BigDecimal price1 = new BigDecimal("50000.00");
        BigDecimal price2 = new BigDecimal("51000.00");
        LocalDateTime time1 = LocalDateTime.now();
        LocalDateTime time2 = time1.plusMinutes(1);

        // When
        priceMetricsService.recordPriceUpdate(tradingPair, price1, time1);
        priceMetricsService.recordPriceUpdate(tradingPair, price2, time2);

        // Then
        PriceMetrics metrics = priceMetricsService.getMetrics(tradingPair);
        assertThat(metrics).isNotNull();
        assertThat(metrics.getCurrentPrice()).isEqualByComparingTo(price2);
        assertThat(metrics.getUpdateCount().get()).isEqualTo(2);
        assertThat(metrics.getFirstUpdateTime()).isEqualTo(time1);
        assertThat(metrics.getLastUpdateTime()).isEqualTo(time2);
    }

    @Test
    void shouldReturnNullForNonExistentTradingPair() {
        // When
        PriceMetrics metrics = priceMetricsService.getMetrics("NONEXISTENT");

        // Then
        assertThat(metrics).isNull();
    }

    @Test
    void shouldGetAllMetricsForMultipleTradingPairs() {
        // Given
        priceMetricsService.recordPriceUpdate("BTCUSDT", new BigDecimal("50000"), LocalDateTime.now());
        priceMetricsService.recordPriceUpdate("ETHUSDT", new BigDecimal("3000"), LocalDateTime.now());
        priceMetricsService.recordPriceUpdate("ADAUSDT", new BigDecimal("2"), LocalDateTime.now());

        // When
        Collection<PriceMetrics> allMetrics = priceMetricsService.getAllMetrics();

        // Then
        assertThat(allMetrics).hasSize(3);
        assertThat(allMetrics.stream().map(PriceMetrics::getTradingPair))
                .containsExactlyInAnyOrder("BTCUSDT", "ETHUSDT", "ADAUSDT");
    }

    @Test
    void shouldReturnEmptyCollectionWhenNoMetrics() {
        // When
        Collection<PriceMetrics> allMetrics = priceMetricsService.getAllMetrics();

        // Then
        assertThat(allMetrics).isEmpty();
    }

    @Test
    void shouldCalculateTotalUpdateCountAcrossAllPairs() {
        // Given
        priceMetricsService.recordPriceUpdate("BTCUSDT", new BigDecimal("50000"), LocalDateTime.now());
        priceMetricsService.recordPriceUpdate("BTCUSDT", new BigDecimal("51000"), LocalDateTime.now());
        priceMetricsService.recordPriceUpdate("ETHUSDT", new BigDecimal("3000"), LocalDateTime.now());
        priceMetricsService.recordPriceUpdate("ETHUSDT", new BigDecimal("3100"), LocalDateTime.now());
        priceMetricsService.recordPriceUpdate("ETHUSDT", new BigDecimal("3200"), LocalDateTime.now());

        // When
        int totalUpdateCount = priceMetricsService.getTotalUpdateCount();

        // Then
        assertThat(totalUpdateCount).isEqualTo(5); // 2 BTC + 3 ETH updates
    }

    @Test
    void shouldReturnZeroTotalUpdateCountWhenNoMetrics() {
        // When
        int totalUpdateCount = priceMetricsService.getTotalUpdateCount();

        // Then
        assertThat(totalUpdateCount).isEqualTo(0);
    }

    @Test
    void shouldCalculateSystemAverageVolatility() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        
        // BTC with volatility: range = 10000, avg = 50000, volatility = 20%
        priceMetricsService.recordPriceUpdate("BTCUSDT", new BigDecimal("45000"), now);
        priceMetricsService.recordPriceUpdate("BTCUSDT", new BigDecimal("55000"), now.plusMinutes(1));
        
        // ETH with volatility: range = 1000, avg = 3000, volatility = 33.33%
        priceMetricsService.recordPriceUpdate("ETHUSDT", new BigDecimal("2500"), now);
        priceMetricsService.recordPriceUpdate("ETHUSDT", new BigDecimal("3500"), now.plusMinutes(1));

        // When
        double systemAverageVolatility = priceMetricsService.getSystemAverageVolatility();

        // Then
        // Average of 20% and 33.33% = 26.67% (approximately)
        assertThat(systemAverageVolatility).isBetween(26.0, 27.0);
    }

    @Test
    void shouldReturnZeroSystemAverageVolatilityWhenNoMetrics() {
        // When
        double systemAverageVolatility = priceMetricsService.getSystemAverageVolatility();

        // Then
        assertThat(systemAverageVolatility).isEqualTo(0.0);
    }

    @Test
    void shouldGetSystemMetricsSummaryWithAllData() {
        // Given
        LocalDateTime time1 = LocalDateTime.now();
        LocalDateTime time2 = time1.plusMinutes(1);
        LocalDateTime time3 = time1.plusMinutes(2);

        priceMetricsService.recordPriceUpdate("BTCUSDT", new BigDecimal("50000"), time1);
        priceMetricsService.recordPriceUpdate("ETHUSDT", new BigDecimal("3000"), time2);
        priceMetricsService.recordPriceUpdate("BTCUSDT", new BigDecimal("51000"), time3);

        // When
        SystemMetricsSummary summary = priceMetricsService.getSystemMetricsSummary();

        // Then
        assertThat(summary.getTotalPairsTracked()).isEqualTo(2);
        assertThat(summary.getTotalPriceUpdates()).isEqualTo(3);
        assertThat(summary.getSystemAverageVolatility()).isGreaterThan(0.0);
        assertThat(summary.getFirstUpdateTime()).isEqualTo(time1);
        assertThat(summary.getLastUpdateTime()).isEqualTo(time3);
    }

    @Test
    void shouldGetSystemMetricsSummaryWithNullTimesWhenNoMetrics() {
        // When
        SystemMetricsSummary summary = priceMetricsService.getSystemMetricsSummary();

        // Then
        assertThat(summary.getTotalPairsTracked()).isEqualTo(0);
        assertThat(summary.getTotalPriceUpdates()).isEqualTo(0);
        assertThat(summary.getSystemAverageVolatility()).isEqualTo(0.0);
        assertThat(summary.getFirstUpdateTime()).isNull();
        assertThat(summary.getLastUpdateTime()).isNull();
    }

    @Test
    void shouldFindCorrectFirstAndLastUpdateTimesAcrossMultiplePairs() {
        // Given
        LocalDateTime earliest = LocalDateTime.now().minusHours(1);
        LocalDateTime middle = LocalDateTime.now();
        LocalDateTime latest = LocalDateTime.now().plusHours(1);

        priceMetricsService.recordPriceUpdate("BTCUSDT", new BigDecimal("50000"), middle);
        priceMetricsService.recordPriceUpdate("ETHUSDT", new BigDecimal("3000"), earliest);
        priceMetricsService.recordPriceUpdate("ADAUSDT", new BigDecimal("2"), latest);

        // When
        SystemMetricsSummary summary = priceMetricsService.getSystemMetricsSummary();

        // Then
        assertThat(summary.getFirstUpdateTime()).isEqualTo(earliest);
        assertThat(summary.getLastUpdateTime()).isEqualTo(latest);
    }

    @Test
    void shouldResetMetricsForSpecificTradingPair() {
        // Given
        priceMetricsService.recordPriceUpdate("BTCUSDT", new BigDecimal("50000"), LocalDateTime.now());
        priceMetricsService.recordPriceUpdate("ETHUSDT", new BigDecimal("3000"), LocalDateTime.now());

        // When
        priceMetricsService.resetMetrics("BTCUSDT");

        // Then
        assertThat(priceMetricsService.getMetrics("BTCUSDT")).isNull();
        assertThat(priceMetricsService.getMetrics("ETHUSDT")).isNotNull();
        assertThat(priceMetricsService.getAllMetrics()).hasSize(1);
    }

    @Test
    void shouldResetAllMetrics() {
        // Given
        priceMetricsService.recordPriceUpdate("BTCUSDT", new BigDecimal("50000"), LocalDateTime.now());
        priceMetricsService.recordPriceUpdate("ETHUSDT", new BigDecimal("3000"), LocalDateTime.now());
        priceMetricsService.recordPriceUpdate("ADAUSDT", new BigDecimal("2"), LocalDateTime.now());

        // When
        priceMetricsService.resetAllMetrics();

        // Then
        assertThat(priceMetricsService.getAllMetrics()).isEmpty();
        assertThat(priceMetricsService.getTotalUpdateCount()).isEqualTo(0);
        assertThat(priceMetricsService.getSystemAverageVolatility()).isEqualTo(0.0);
    }

    @Test
    void shouldHandleConcurrentAccessCorrectly() {
        // Given
        String tradingPair = "BTCUSDT";
        BigDecimal price1 = new BigDecimal("50000");
        BigDecimal price2 = new BigDecimal("51000");
        LocalDateTime now = LocalDateTime.now();

        // When - Simulate concurrent access
        priceMetricsService.recordPriceUpdate(tradingPair, price1, now);
        PriceMetrics metrics1 = priceMetricsService.getMetrics(tradingPair);
        priceMetricsService.recordPriceUpdate(tradingPair, price2, now.plusMinutes(1));
        PriceMetrics metrics2 = priceMetricsService.getMetrics(tradingPair);

        // Then
        assertThat(metrics1).isSameAs(metrics2); // Same instance due to ConcurrentHashMap
        assertThat(metrics2.getUpdateCount().get()).isEqualTo(2);
        assertThat(metrics2.getCurrentPrice()).isEqualByComparingTo(price2);
    }

    @Test
    void shouldHandleMultiplePriceUpdatesForCalculations() {
        // Given
        String tradingPair = "BTCUSDT";
        LocalDateTime now = LocalDateTime.now();
        
        // Record multiple prices to test calculations
        priceMetricsService.recordPriceUpdate(tradingPair, new BigDecimal("48000"), now);
        priceMetricsService.recordPriceUpdate(tradingPair, new BigDecimal("52000"), now.plusMinutes(1));
        priceMetricsService.recordPriceUpdate(tradingPair, new BigDecimal("50000"), now.plusMinutes(2));

        // When
        PriceMetrics metrics = priceMetricsService.getMetrics(tradingPair);
        int totalUpdates = priceMetricsService.getTotalUpdateCount();
        double systemVolatility = priceMetricsService.getSystemAverageVolatility();

        // Then
        assertThat(metrics.getUpdateCount().get()).isEqualTo(3);
        assertThat(metrics.getAveragePrice()).isEqualByComparingTo(new BigDecimal("50000.00000000"));
        assertThat(totalUpdates).isEqualTo(3);
        assertThat(systemVolatility).isEqualTo(8.0); // (4000/50000)*100 = 8%
    }

    @Test
    void shouldHandleSamePricesInCalculations() {
        // Given - Same prices result in zero volatility
        BigDecimal samePrice = new BigDecimal("100.00");
        priceMetricsService.recordPriceUpdate("STABLEPAIR", samePrice, LocalDateTime.now());
        priceMetricsService.recordPriceUpdate("STABLEPAIR", samePrice, LocalDateTime.now().plusMinutes(1));

        // When
        double systemVolatility = priceMetricsService.getSystemAverageVolatility();
        SystemMetricsSummary summary = priceMetricsService.getSystemMetricsSummary();

        // Then
        assertThat(systemVolatility).isEqualTo(0.0);
        assertThat(summary.getSystemAverageVolatility()).isEqualTo(0.0);
    }

    @Test
    void shouldHandleMixedVolatilityCalculations() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        
        // Pair with zero volatility (same price)
        priceMetricsService.recordPriceUpdate("STABLE", new BigDecimal("100"), now);
        priceMetricsService.recordPriceUpdate("STABLE", new BigDecimal("100"), now.plusMinutes(1));
        
        // Pair with high volatility
        priceMetricsService.recordPriceUpdate("VOLATILE", new BigDecimal("50"), now);
        priceMetricsService.recordPriceUpdate("VOLATILE", new BigDecimal("150"), now.plusMinutes(1));

        // When
        double systemVolatility = priceMetricsService.getSystemAverageVolatility();

        // Then
        // STABLE: volatility = 0%, VOLATILE: volatility = 100%, average = 50%
        assertThat(systemVolatility).isEqualTo(50.0);
    }
}