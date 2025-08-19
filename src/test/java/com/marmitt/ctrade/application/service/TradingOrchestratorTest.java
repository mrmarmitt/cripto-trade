package com.marmitt.ctrade.application.service;

import com.marmitt.ctrade.domain.entity.MarketData;
import com.marmitt.ctrade.domain.entity.Order;
import com.marmitt.ctrade.domain.entity.Portfolio;
import com.marmitt.ctrade.domain.entity.TradingPair;
import com.marmitt.ctrade.domain.port.ExchangePort;
import com.marmitt.ctrade.domain.port.TradingStrategy;
import com.marmitt.ctrade.domain.valueobject.SignalType;
import com.marmitt.ctrade.domain.valueobject.StrategySignal;
import com.marmitt.ctrade.infrastructure.config.StrategyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TradingOrchestratorTest {

    @Mock
    private StrategyRegistry strategyRegistry;

    @Mock
    private ExchangePort exchangePort;

    @Mock
    private StrategyProperties strategyProperties;

    @Mock
    private TradingStrategy mockStrategy;

    @InjectMocks
    private TradingOrchestrator tradingOrchestrator;

    private MarketData marketData;
    private TradingPair tradingPair;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        tradingPair = new TradingPair("BTC", "USDT");
        marketData = new MarketData(tradingPair, new BigDecimal("50000"), LocalDateTime.now());
        portfolio = new Portfolio();
        
        lenient().when(mockStrategy.getStrategyName()).thenReturn("TestStrategy");
        lenient().when(strategyRegistry.getRegisteredCount()).thenReturn(1);
    }

    @Test
    void shouldInitializeCorrectly() {
        tradingOrchestrator.initialize();

        verify(strategyRegistry).getRegisteredCount();
    }

    @Test
    void shouldSkipExecutionWhenMarketDataIsNull() {
        when(strategyRegistry.getActiveStrategies()).thenReturn(Collections.singletonList(mockStrategy));

        tradingOrchestrator.executeStrategies(null);

        verify(mockStrategy, never()).analyze(any(), any());
    }

    @Test
    void shouldSkipExecutionWhenNoActiveStrategies() {
        when(strategyRegistry.getActiveStrategies()).thenReturn(Collections.emptyList());

        tradingOrchestrator.executeStrategies(marketData);

        verify(mockStrategy, never()).analyze(any(), any());
    }

    @Test
    void shouldExecuteActiveStrategies() throws InterruptedException {
        List<TradingStrategy> activeStrategies = Collections.singletonList(mockStrategy);
        when(strategyRegistry.getActiveStrategies()).thenReturn(activeStrategies);
        when(mockStrategy.analyze(any(MarketData.class), any(Portfolio.class))).thenReturn(null);

        tradingOrchestrator.executeStrategies(marketData);

        Thread.sleep(100); // Wait for async execution
        verify(mockStrategy).analyze(eq(marketData), any(Portfolio.class));
    }

    @Test
    void shouldProcessActionableSignal() throws InterruptedException {
        StrategySignal signal = createValidBuySignal();
        when(strategyRegistry.getActiveStrategies()).thenReturn(Collections.singletonList(mockStrategy));
        when(mockStrategy.analyze(any(MarketData.class), any(Portfolio.class))).thenReturn(signal);
        
        StrategyProperties.StrategyConfig config = new StrategyProperties.StrategyConfig();
        config.setMaxOrderValue(new BigDecimal("10000"));
        config.setMinOrderValue(new BigDecimal("10"));
        when(strategyProperties.getStrategyConfig("TestStrategy")).thenReturn(config);

        tradingOrchestrator.executeStrategies(marketData);

        Thread.sleep(100); // Wait for async execution
        verify(exchangePort).placeOrder(any(Order.class));
    }

    @Test
    void shouldSkipNonActionableSignal() throws InterruptedException {
        StrategySignal signal = mock(StrategySignal.class);
        when(signal.isActionable()).thenReturn(false);
        
        when(strategyRegistry.getActiveStrategies()).thenReturn(Collections.singletonList(mockStrategy));
        when(mockStrategy.analyze(any(MarketData.class), any(Portfolio.class))).thenReturn(signal);

        tradingOrchestrator.executeStrategies(marketData);

        Thread.sleep(100); // Wait for async execution
        verify(exchangePort, never()).placeOrder(any(Order.class));
    }

    @Test
    void shouldValidateSignalCorrectly() throws InterruptedException {
        StrategySignal invalidSignal = createInvalidSignal();
        when(strategyRegistry.getActiveStrategies()).thenReturn(Collections.singletonList(mockStrategy));
        when(mockStrategy.analyze(any(MarketData.class), any(Portfolio.class))).thenReturn(invalidSignal);

        tradingOrchestrator.executeStrategies(marketData);

        Thread.sleep(100); // Wait for async execution
        verify(exchangePort, never()).placeOrder(any(Order.class));
    }

    @Test
    void shouldRejectSignalExceedingMaxOrderValue() throws InterruptedException {
        StrategySignal signal = createValidBuySignal();
        when(strategyRegistry.getActiveStrategies()).thenReturn(Collections.singletonList(mockStrategy));
        when(mockStrategy.analyze(any(MarketData.class), any(Portfolio.class))).thenReturn(signal);
        
        StrategyProperties.StrategyConfig config = new StrategyProperties.StrategyConfig();
        config.setMaxOrderValue(new BigDecimal("50")); // Less than signal value (100 * 1 = 100)
        config.setMinOrderValue(new BigDecimal("10"));
        when(strategyProperties.getStrategyConfig("TestStrategy")).thenReturn(config);

        tradingOrchestrator.executeStrategies(marketData);

        Thread.sleep(100); // Wait for async execution
        verify(exchangePort, never()).placeOrder(any(Order.class));
    }

    @Test
    void shouldRejectSignalBelowMinOrderValue() throws InterruptedException {
        StrategySignal signal = createValidBuySignal();
        when(strategyRegistry.getActiveStrategies()).thenReturn(Collections.singletonList(mockStrategy));
        when(mockStrategy.analyze(any(MarketData.class), any(Portfolio.class))).thenReturn(signal);
        
        StrategyProperties.StrategyConfig config = new StrategyProperties.StrategyConfig();
        config.setMaxOrderValue(new BigDecimal("10000"));
        config.setMinOrderValue(new BigDecimal("200")); // Greater than signal value (100 * 1 = 100)
        when(strategyProperties.getStrategyConfig("TestStrategy")).thenReturn(config);

        tradingOrchestrator.executeStrategies(marketData);

        Thread.sleep(100); // Wait for async execution
        verify(exchangePort, never()).placeOrder(any(Order.class));
    }

    @Test
    void shouldCreateCorrectBuyOrderFromSignal() throws InterruptedException {
        StrategySignal signal = createValidBuySignal();
        when(strategyRegistry.getActiveStrategies()).thenReturn(Collections.singletonList(mockStrategy));
        when(mockStrategy.analyze(any(MarketData.class), any(Portfolio.class))).thenReturn(signal);
        
        StrategyProperties.StrategyConfig config = new StrategyProperties.StrategyConfig();
        config.setMaxOrderValue(new BigDecimal("10000"));
        config.setMinOrderValue(new BigDecimal("10"));
        when(strategyProperties.getStrategyConfig("TestStrategy")).thenReturn(config);

        tradingOrchestrator.executeStrategies(marketData);

        Thread.sleep(100); // Wait for async execution
        
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(exchangePort).placeOrder(orderCaptor.capture());
        
        Order capturedOrder = orderCaptor.getValue();
        assertEquals(Order.OrderSide.BUY, capturedOrder.getSide());
        assertEquals(Order.OrderType.LIMIT, capturedOrder.getType());
        assertEquals(new BigDecimal("1"), capturedOrder.getQuantity());
        assertEquals(new BigDecimal("100"), capturedOrder.getPrice());
    }

    @Test
    void shouldCreateCorrectSellOrderFromSignal() throws InterruptedException {
        StrategySignal signal = createValidSellSignal();
        when(strategyRegistry.getActiveStrategies()).thenReturn(Collections.singletonList(mockStrategy));
        when(mockStrategy.analyze(any(MarketData.class), any(Portfolio.class))).thenReturn(signal);
        
        StrategyProperties.StrategyConfig config = new StrategyProperties.StrategyConfig();
        config.setMaxOrderValue(new BigDecimal("10000"));
        config.setMinOrderValue(new BigDecimal("10"));
        when(strategyProperties.getStrategyConfig("TestStrategy")).thenReturn(config);

        tradingOrchestrator.executeStrategies(marketData);

        Thread.sleep(100); // Wait for async execution
        
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(exchangePort).placeOrder(orderCaptor.capture());
        
        Order capturedOrder = orderCaptor.getValue();
        assertEquals(Order.OrderSide.SELL, capturedOrder.getSide());
    }

    @Test
    void shouldUpdatePortfolioCorrectly() {
        Portfolio newPortfolio = new Portfolio();
        newPortfolio.addToBalance("BTC", new BigDecimal("1"));

        tradingOrchestrator.updatePortfolio(newPortfolio);

        assertEquals(newPortfolio, tradingOrchestrator.getCurrentPortfolio());
    }

    @Test
    void shouldEnableStrategy() {
        tradingOrchestrator.enableStrategy("TestStrategy");

        verify(strategyRegistry).enableStrategy("TestStrategy");
    }

    @Test
    void shouldDisableStrategy() {
        tradingOrchestrator.disableStrategy("TestStrategy");

        verify(strategyRegistry).disableStrategy("TestStrategy");
    }

    @Test
    void shouldReturnActiveStrategyCount() {
        when(strategyRegistry.getActiveCount()).thenReturn(3);

        int count = tradingOrchestrator.getActiveStrategyCount();

        assertEquals(3, count);
        verify(strategyRegistry).getActiveCount();
    }

    @Test
    void shouldReturnActiveStrategies() {
        List<TradingStrategy> strategies = Arrays.asList(mockStrategy);
        when(strategyRegistry.getActiveStrategies()).thenReturn(strategies);

        List<TradingStrategy> result = tradingOrchestrator.getActiveStrategies();

        assertEquals(strategies, result);
        verify(strategyRegistry).getActiveStrategies();
    }

    @Test
    void shouldHandleStrategyAnalysisException() throws InterruptedException {
        when(strategyRegistry.getActiveStrategies()).thenReturn(Collections.singletonList(mockStrategy));
        when(mockStrategy.analyze(any(MarketData.class), any(Portfolio.class)))
                .thenThrow(new RuntimeException("Analysis failed"));

        tradingOrchestrator.executeStrategies(marketData);

        Thread.sleep(100); // Wait for async execution
        verify(exchangePort, never()).placeOrder(any(Order.class));
    }

    @Test
    void shouldHandleExchangePortException() throws InterruptedException {
        StrategySignal signal = createValidBuySignal();
        when(strategyRegistry.getActiveStrategies()).thenReturn(Collections.singletonList(mockStrategy));
        when(mockStrategy.analyze(any(MarketData.class), any(Portfolio.class))).thenReturn(signal);
        
        StrategyProperties.StrategyConfig config = new StrategyProperties.StrategyConfig();
        config.setMaxOrderValue(new BigDecimal("10000"));
        config.setMinOrderValue(new BigDecimal("10"));
        when(strategyProperties.getStrategyConfig("TestStrategy")).thenReturn(config);
        
        doThrow(new RuntimeException("Exchange error")).when(exchangePort).placeOrder(any(Order.class));

        tradingOrchestrator.executeStrategies(marketData);

        Thread.sleep(100); // Wait for async execution
        verify(exchangePort).placeOrder(any(Order.class));
    }

    private StrategySignal createValidBuySignal() {
        StrategySignal signal = mock(StrategySignal.class);
        when(signal.isActionable()).thenReturn(true);
        when(signal.getStrategyName()).thenReturn("TestStrategy");
        when(signal.getType()).thenReturn(SignalType.BUY);
        when(signal.getPair()).thenReturn(tradingPair);
        when(signal.getQuantity()).thenReturn(new BigDecimal("1"));
        when(signal.getPrice()).thenReturn(new BigDecimal("100"));
        when(signal.getReason()).thenReturn("Test signal");
        return signal;
    }

    private StrategySignal createValidSellSignal() {
        StrategySignal signal = mock(StrategySignal.class);
        when(signal.isActionable()).thenReturn(true);
        when(signal.getStrategyName()).thenReturn("TestStrategy");
        when(signal.getType()).thenReturn(SignalType.SELL);
        when(signal.getPair()).thenReturn(tradingPair);
        when(signal.getQuantity()).thenReturn(new BigDecimal("1"));
        when(signal.getPrice()).thenReturn(new BigDecimal("100"));
        when(signal.getReason()).thenReturn("Test signal");
        return signal;
    }

    private StrategySignal createInvalidSignal() {
        StrategySignal signal = mock(StrategySignal.class);
        when(signal.isActionable()).thenReturn(true);
        when(signal.getPair()).thenReturn(null); // Invalid - null pair
        when(signal.getQuantity()).thenReturn(new BigDecimal("1"));
        when(signal.getPrice()).thenReturn(new BigDecimal("100"));
        return signal;
    }
}