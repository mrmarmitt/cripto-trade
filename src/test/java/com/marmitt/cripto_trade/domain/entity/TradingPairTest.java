package com.marmitt.cripto_trade.domain.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class TradingPairTest {

    @Test
    @DisplayName("Should create trading pair with valid base and quote currencies")
    void shouldCreateTradingPairWithValidCurrencies() {
        TradingPair tradingPair = new TradingPair("BTC", "USD");
        
        assertThat(tradingPair.getBaseCurrency()).isEqualTo("BTC");
        assertThat(tradingPair.getQuoteCurrency()).isEqualTo("USD");
        assertThat(tradingPair.getSymbol()).isEqualTo("BTC/USD");
    }

    @Test
    @DisplayName("Should create trading pair from symbol string")
    void shouldCreateTradingPairFromSymbol() {
        TradingPair tradingPair = new TradingPair("btc/usd");
        
        assertThat(tradingPair.getBaseCurrency()).isEqualTo("BTC");
        assertThat(tradingPair.getQuoteCurrency()).isEqualTo("USD");
        assertThat(tradingPair.getSymbol()).isEqualTo("BTC/USD");
    }

    @ParameterizedTest
    @DisplayName("Should throw exception for invalid symbols")
    @ValueSource(strings = {"BTCUSD", "BTC-USD", "BTC", "", "BTC/USD/EUR"})
    void shouldThrowExceptionForInvalidSymbols(String invalidSymbol) {
        assertThatThrownBy(() -> new TradingPair(invalidSymbol))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid trading pair symbol");
    }

    @Test
    @DisplayName("Should throw exception for null symbol")
    void shouldThrowExceptionForNullSymbol() {
        assertThatThrownBy(() -> new TradingPair(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid trading pair symbol");
    }

    @Test
    @DisplayName("Should be equal when same base and quote currencies")
    void shouldBeEqualWhenSameCurrencies() {
        TradingPair pair1 = new TradingPair("BTC", "USD");
        TradingPair pair2 = new TradingPair("BTC", "USD");
        TradingPair pair3 = new TradingPair("btc/usd");
        
        assertThat(pair1).isEqualTo(pair2);
        assertThat(pair1).isEqualTo(pair3);
        assertThat(pair1.hashCode()).isEqualTo(pair2.hashCode());
    }

    @Test
    @DisplayName("Should not be equal when different currencies")
    void shouldNotBeEqualWhenDifferentCurrencies() {
        TradingPair pair1 = new TradingPair("BTC", "USD");
        TradingPair pair2 = new TradingPair("ETH", "USD");
        TradingPair pair3 = new TradingPair("BTC", "EUR");
        
        assertThat(pair1).isNotEqualTo(pair2);
        assertThat(pair1).isNotEqualTo(pair3);
    }
}