package com.marmitt.ctrade.domain.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioTest {
    
    private Portfolio portfolio;
    
    @BeforeEach
    void setUp() {
        portfolio = new Portfolio();
    }
    
    @Test
    void shouldReturnZeroForNonExistentCurrency() {
        BigDecimal balance = portfolio.getBalance("BTC");
        assertEquals(BigDecimal.ZERO, balance);
    }
    
    @Test
    void shouldUpdateBalance() {
        portfolio.updateBalance("BTC", BigDecimal.ONE);
        
        BigDecimal balance = portfolio.getBalance("BTC");
        assertEquals(BigDecimal.ONE, balance);
    }
    
    @Test
    void shouldAddToBalance() {
        portfolio.updateBalance("BTC", BigDecimal.ONE);
        portfolio.addToBalance("BTC", BigDecimal.valueOf(0.5));
        
        BigDecimal balance = portfolio.getBalance("BTC");
        assertEquals(BigDecimal.valueOf(1.5), balance);
    }
    
    @Test
    void shouldAddToBalanceForNewCurrency() {
        portfolio.addToBalance("ETH", BigDecimal.valueOf(2.0));
        
        BigDecimal balance = portfolio.getBalance("ETH");
        assertEquals(BigDecimal.valueOf(2.0), balance);
    }
    
    @Test
    void shouldSubtractFromBalance() {
        portfolio.updateBalance("BTC", BigDecimal.valueOf(2.0));
        portfolio.subtractFromBalance("BTC", BigDecimal.ONE);
        
        BigDecimal balance = portfolio.getBalance("BTC");
        assertEquals(0, BigDecimal.ONE.compareTo(balance));
    }
    
    @Test
    void shouldThrowExceptionWhenSubtractingMoreThanBalance() {
        portfolio.updateBalance("BTC", BigDecimal.ONE);
        
        assertThrows(IllegalArgumentException.class, () -> {
            portfolio.subtractFromBalance("BTC", BigDecimal.valueOf(2.0));
        });
    }
    
    @Test
    void shouldThrowExceptionWhenSubtractingFromZeroBalance() {
        assertThrows(IllegalArgumentException.class, () -> {
            portfolio.subtractFromBalance("BTC", BigDecimal.ONE);
        });
    }
    
    @Test
    void shouldCheckSufficientBalance() {
        portfolio.updateBalance("USDT", BigDecimal.valueOf(1000));
        
        assertTrue(portfolio.hasBalance("USDT", BigDecimal.valueOf(500)));
        assertTrue(portfolio.hasBalance("USDT", BigDecimal.valueOf(1000)));
        assertFalse(portfolio.hasBalance("USDT", BigDecimal.valueOf(1001)));
    }
    
    @Test
    void shouldReturnFalseForInsufficientBalance() {
        assertFalse(portfolio.hasBalance("BTC", BigDecimal.ONE));
    }
    
    @Test
    void shouldHandleMultipleCurrencies() {
        portfolio.updateBalance("BTC", BigDecimal.ONE);
        portfolio.updateBalance("ETH", BigDecimal.valueOf(10));
        portfolio.updateBalance("USDT", BigDecimal.valueOf(50000));
        
        assertEquals(BigDecimal.ONE, portfolio.getBalance("BTC"));
        assertEquals(BigDecimal.valueOf(10), portfolio.getBalance("ETH"));
        assertEquals(BigDecimal.valueOf(50000), portfolio.getBalance("USDT"));
        assertEquals(3, portfolio.getHoldings().size());
    }
    
    @Test
    void shouldHandleZeroAmounts() {
        portfolio.updateBalance("BTC", BigDecimal.ZERO);
        portfolio.addToBalance("BTC", BigDecimal.ZERO);
        portfolio.subtractFromBalance("BTC", BigDecimal.ZERO);
        
        assertEquals(BigDecimal.ZERO, portfolio.getBalance("BTC"));
    }
}