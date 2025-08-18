package com.marmitt.ctrade.infrastructure.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários para ConfigurationBasedTradingPairProvider.
 */
class ConfigurationBasedTradingPairProviderTest {

    private ConfigurationBasedTradingPairProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ConfigurationBasedTradingPairProvider();
    }

    @Test
    void shouldReturnDefaultTradingPairsWhenNoneConfigured() {
        // When
        List<String> tradingPairs = provider.getActiveTradingPairs();
        
        // Then
        assertThat(tradingPairs).containsExactly("BTCUSDT", "BTCUSDC", "USDCUSDT");
    }

    @Test
    void shouldReturnConfiguredTradingPairs() {
        // Given
        provider.setActive(List.of("ETHUSDT", "ADAUSDT", "DOTUSDT"));
        
        // When
        List<String> tradingPairs = provider.getActiveTradingPairs();
        
        // Then
        assertThat(tradingPairs).containsExactly("ETHUSDT", "ADAUSDT", "DOTUSDT");
    }

    @Test
    void shouldCheckIfTradingPairIsActive() {
        // Given
        provider.setActive(List.of("BTCUSDT", "ETHUSDT"));
        
        // When & Then
        assertThat(provider.isActiveTradingPair("BTCUSDT")).isTrue();
        assertThat(provider.isActiveTradingPair("ETHUSDT")).isTrue();
        assertThat(provider.isActiveTradingPair("ADAUSDT")).isFalse();
        assertThat(provider.isActiveTradingPair("BTCUSDT")).isTrue(); // Should match exact case
        assertThat(provider.isActiveTradingPair(null)).isFalse();
    }

    @Test
    void shouldFormatStreamListCorrectly() {
        // Given
        provider.setActive(List.of("BTCUSDT", "ETHUSDT", "ADAUSDT"));
        provider.setStreamFormat("ticker");
        
        // When
        String formatted = provider.getFormattedStreamList();
        
        // Then
        assertThat(formatted).isEqualTo("btcusdt@ticker/ethusdt@ticker/adausdt@ticker");
    }

    @Test
    void shouldFormatStreamListWithCustomFormat() {
        // Given
        provider.setActive(List.of("BTCUSDT", "ETHUSDT"));
        provider.setStreamFormat("trade");
        
        // When
        String formatted = provider.getFormattedStreamList();
        
        // Then
        assertThat(formatted).isEqualTo("btcusdt@trade/ethusdt@trade");
    }

    @Test
    void shouldFormatDefaultTradingPairsWhenNoneConfigured() {
        // Given
        provider.setStreamFormat("ticker");
        
        // When
        String formatted = provider.getFormattedStreamList();
        
        // Then
        assertThat(formatted).isEqualTo("btcusdt@ticker/btcusdc@ticker/usdcusdt@ticker");
    }

    @Test
    void shouldHandleEmptyActiveList() {
        // Given
        provider.setActive(List.of());
        
        // When
        List<String> tradingPairs = provider.getActiveTradingPairs();
        
        // Then - Should return defaults
        assertThat(tradingPairs).containsExactly("BTCUSDT", "BTCUSDC", "USDCUSDT");
    }

    @Test
    void shouldReturnCopyOfActiveList() {
        // Given
        provider.setActive(List.of("BTCUSDT", "ETHUSDT"));
        
        // When
        List<String> tradingPairs1 = provider.getActiveTradingPairs();
        List<String> tradingPairs2 = provider.getActiveTradingPairs();
        
        // Then
        assertThat(tradingPairs1).isNotSameAs(tradingPairs2);
        assertThat(tradingPairs1).isEqualTo(tradingPairs2);
    }
}