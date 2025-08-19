package com.marmitt.ctrade.application.listener;

import com.marmitt.ctrade.application.service.TradingOrchestrator;
import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.domain.entity.MarketData;
import com.marmitt.ctrade.domain.entity.TradingPair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradingStrategyListenerTest {

    @Mock
    private TradingOrchestrator tradingOrchestrator;

    @InjectMocks
    private TradingStrategyListener tradingStrategyListener;

    private PriceUpdateMessage priceUpdateMessage;
    private LocalDateTime testTimestamp;

    @BeforeEach
    void setUp() {
        testTimestamp = LocalDateTime.now();
        priceUpdateMessage = new PriceUpdateMessage();
        priceUpdateMessage.setTradingPair("BTCUSDT");
        priceUpdateMessage.setPrice(new BigDecimal("50000"));
        priceUpdateMessage.setTimestamp(testTimestamp);
    }

    @Test
    void shouldExecuteStrategiesWhenValidPriceUpdateReceived() {
        tradingStrategyListener.onPriceUpdate(priceUpdateMessage);

        ArgumentCaptor<MarketData> marketDataCaptor = ArgumentCaptor.forClass(MarketData.class);
        verify(tradingOrchestrator).executeStrategies(marketDataCaptor.capture());

        MarketData capturedMarketData = marketDataCaptor.getValue();
        assertNotNull(capturedMarketData);
        assertEquals(testTimestamp, capturedMarketData.getTimestamp());
        
        TradingPair expectedPair = new TradingPair("BTC", "USDT");
        assertTrue(capturedMarketData.hasPriceFor(expectedPair));
        assertEquals(new BigDecimal("50000"), capturedMarketData.getPriceFor(expectedPair));
    }

    @Test
    void shouldHandleNullPriceUpdateMessage() {
        tradingStrategyListener.onPriceUpdate(null);

        verify(tradingOrchestrator, never()).executeStrategies(any(MarketData.class));
    }

    @Test
    void shouldHandleNullTradingPair() {
        priceUpdateMessage.setTradingPair(null);

        tradingStrategyListener.onPriceUpdate(priceUpdateMessage);

        verify(tradingOrchestrator, never()).executeStrategies(any(MarketData.class));
    }

    @Test
    void shouldHandleNullPrice() {
        priceUpdateMessage.setPrice(null);

        tradingStrategyListener.onPriceUpdate(priceUpdateMessage);

        verify(tradingOrchestrator, never()).executeStrategies(any(MarketData.class));
    }

    @Test
    void shouldHandleEmptyTradingPair() {
        priceUpdateMessage.setTradingPair("");

        tradingStrategyListener.onPriceUpdate(priceUpdateMessage);

        verify(tradingOrchestrator, never()).executeStrategies(any(MarketData.class));
    }

    @Test
    void shouldUseCurrentTimestampWhenMessageTimestampIsNull() {
        priceUpdateMessage.setTimestamp(null);

        tradingStrategyListener.onPriceUpdate(priceUpdateMessage);

        ArgumentCaptor<MarketData> marketDataCaptor = ArgumentCaptor.forClass(MarketData.class);
        verify(tradingOrchestrator).executeStrategies(marketDataCaptor.capture());

        MarketData capturedMarketData = marketDataCaptor.getValue();
        assertNotNull(capturedMarketData.getTimestamp());
        assertTrue(capturedMarketData.getTimestamp().isAfter(testTimestamp.minusSeconds(5)));
    }

    @Test
    void shouldParseBTCUSDTCorrectly() {
        priceUpdateMessage.setTradingPair("BTCUSDT");

        tradingStrategyListener.onPriceUpdate(priceUpdateMessage);

        ArgumentCaptor<MarketData> marketDataCaptor = ArgumentCaptor.forClass(MarketData.class);
        verify(tradingOrchestrator).executeStrategies(marketDataCaptor.capture());

        MarketData capturedMarketData = marketDataCaptor.getValue();
        TradingPair expectedPair = new TradingPair("BTC", "USDT");
        assertTrue(capturedMarketData.hasPriceFor(expectedPair));
    }

    @Test
    void shouldParseETHBTCCorrectly() {
        priceUpdateMessage.setTradingPair("ETHBTC");

        tradingStrategyListener.onPriceUpdate(priceUpdateMessage);

        ArgumentCaptor<MarketData> marketDataCaptor = ArgumentCaptor.forClass(MarketData.class);
        verify(tradingOrchestrator).executeStrategies(marketDataCaptor.capture());

        MarketData capturedMarketData = marketDataCaptor.getValue();
        TradingPair expectedPair = new TradingPair("ETH", "BTC");
        assertTrue(capturedMarketData.hasPriceFor(expectedPair));
    }

    @Test
    void shouldParseBNBETHCorrectly() {
        priceUpdateMessage.setTradingPair("BNBETH");

        tradingStrategyListener.onPriceUpdate(priceUpdateMessage);

        ArgumentCaptor<MarketData> marketDataCaptor = ArgumentCaptor.forClass(MarketData.class);
        verify(tradingOrchestrator).executeStrategies(marketDataCaptor.capture());

        MarketData capturedMarketData = marketDataCaptor.getValue();
        TradingPair expectedPair = new TradingPair("BNB", "ETH");
        assertTrue(capturedMarketData.hasPriceFor(expectedPair));
    }

    @Test
    void shouldParseTradingPairWithDashSeparator() {
        priceUpdateMessage.setTradingPair("BTC-USDT");

        tradingStrategyListener.onPriceUpdate(priceUpdateMessage);

        ArgumentCaptor<MarketData> marketDataCaptor = ArgumentCaptor.forClass(MarketData.class);
        verify(tradingOrchestrator).executeStrategies(marketDataCaptor.capture());

        MarketData capturedMarketData = marketDataCaptor.getValue();
        TradingPair expectedPair = new TradingPair("BTC", "USDT");
        assertTrue(capturedMarketData.hasPriceFor(expectedPair));
    }

    @Test
    void shouldParseTradingPairWithSlashSeparator() {
        priceUpdateMessage.setTradingPair("BTC/USDT");

        tradingStrategyListener.onPriceUpdate(priceUpdateMessage);

        ArgumentCaptor<MarketData> marketDataCaptor = ArgumentCaptor.forClass(MarketData.class);
        verify(tradingOrchestrator).executeStrategies(marketDataCaptor.capture());

        MarketData capturedMarketData = marketDataCaptor.getValue();
        TradingPair expectedPair = new TradingPair("BTC", "USDT");
        assertTrue(capturedMarketData.hasPriceFor(expectedPair));
    }

    @Test
    void shouldHandleLowercaseTradingPair() {
        priceUpdateMessage.setTradingPair("btcusdt");

        tradingStrategyListener.onPriceUpdate(priceUpdateMessage);

        ArgumentCaptor<MarketData> marketDataCaptor = ArgumentCaptor.forClass(MarketData.class);
        verify(tradingOrchestrator).executeStrategies(marketDataCaptor.capture());

        MarketData capturedMarketData = marketDataCaptor.getValue();
        TradingPair expectedPair = new TradingPair("BTC", "USDT");
        assertTrue(capturedMarketData.hasPriceFor(expectedPair));
    }

    @Test
    void shouldParseBUSDPairCorrectly() {
        priceUpdateMessage.setTradingPair("ETHBUSD");

        tradingStrategyListener.onPriceUpdate(priceUpdateMessage);

        ArgumentCaptor<MarketData> marketDataCaptor = ArgumentCaptor.forClass(MarketData.class);
        verify(tradingOrchestrator).executeStrategies(marketDataCaptor.capture());

        MarketData capturedMarketData = marketDataCaptor.getValue();
        TradingPair expectedPair = new TradingPair("ETH", "BUSD");
        assertTrue(capturedMarketData.hasPriceFor(expectedPair));
    }

    @Test
    void shouldParseBNBPairCorrectly() {
        priceUpdateMessage.setTradingPair("ETHBNB");

        tradingStrategyListener.onPriceUpdate(priceUpdateMessage);

        ArgumentCaptor<MarketData> marketDataCaptor = ArgumentCaptor.forClass(MarketData.class);
        verify(tradingOrchestrator).executeStrategies(marketDataCaptor.capture());

        MarketData capturedMarketData = marketDataCaptor.getValue();
        TradingPair expectedPair = new TradingPair("ETH", "BNB");
        assertTrue(capturedMarketData.hasPriceFor(expectedPair));
    }

    @Test
    void shouldFallbackToGenericParsingForUnknownPairs() {
        priceUpdateMessage.setTradingPair("DOTKSM");

        tradingStrategyListener.onPriceUpdate(priceUpdateMessage);

        ArgumentCaptor<MarketData> marketDataCaptor = ArgumentCaptor.forClass(MarketData.class);
        verify(tradingOrchestrator).executeStrategies(marketDataCaptor.capture());

        MarketData capturedMarketData = marketDataCaptor.getValue();
        TradingPair expectedPair = new TradingPair("DOT", "KSM");
        assertTrue(capturedMarketData.hasPriceFor(expectedPair));
    }

    @Test
    void shouldRejectTooShortTradingPairs() {
        priceUpdateMessage.setTradingPair("BTCX");

        tradingStrategyListener.onPriceUpdate(priceUpdateMessage);

        verify(tradingOrchestrator, never()).executeStrategies(any(MarketData.class));
    }

    @Test
    void shouldHandleExceptionInTradingOrchestrator() {
        doThrow(new RuntimeException("Orchestrator error"))
                .when(tradingOrchestrator).executeStrategies(any(MarketData.class));

        assertDoesNotThrow(() -> tradingStrategyListener.onPriceUpdate(priceUpdateMessage));

        verify(tradingOrchestrator).executeStrategies(any(MarketData.class));
    }
}