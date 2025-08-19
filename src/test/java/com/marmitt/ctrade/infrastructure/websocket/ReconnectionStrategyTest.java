package com.marmitt.ctrade.infrastructure.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ReconnectionStrategyTest {
    
    private ReconnectionStrategy strategy;
    
    @BeforeEach
    void setUp() {
        strategy = new ReconnectionStrategy();
    }
    
    @Test
    void shouldAllowReconnectionInitially() {
        assertThat(strategy.shouldReconnect()).isTrue();
        assertThat(strategy.getCurrentAttempt()).isEqualTo(0);
    }
    
    @Test
    void shouldUseExponentialBackoff() {
        // First attempt
        Duration delay1 = strategy.getNextDelay();
        strategy.recordAttempt();
        assertThat(delay1).isEqualTo(Duration.ofSeconds(1));
        
        // Second attempt
        Duration delay2 = strategy.getNextDelay();
        strategy.recordAttempt();
        assertThat(delay2).isEqualTo(Duration.ofSeconds(2));
        
        // Third attempt
        Duration delay3 = strategy.getNextDelay();
        strategy.recordAttempt();
        assertThat(delay3).isEqualTo(Duration.ofSeconds(4));
    }
    
    @Test
    void shouldReachMaxAttempts() {
        for (int i = 0; i < 10; i++) {
            strategy.recordAttempt();
        }
        
        assertThat(strategy.shouldReconnect()).isFalse();
        assertThat(strategy.isMaxAttemptsReached()).isTrue();
        assertThat(strategy.getCurrentAttempt()).isEqualTo(10);
    }
    
    @Test
    void shouldResetCorrectly() {
        strategy.recordAttempt();
        strategy.recordAttempt();
        
        assertThat(strategy.getCurrentAttempt()).isEqualTo(2);
        
        strategy.reset();
        
        assertThat(strategy.getCurrentAttempt()).isEqualTo(0);
        assertThat(strategy.shouldReconnect()).isTrue();
        assertThat(strategy.getLastAttempt()).isNull();
    }
    
    @Test
    void shouldCapMaxDelay() {
        // Force many attempts to test max delay
        for (int i = 0; i < 15; i++) {
            strategy.recordAttempt();
        }
        
        Duration delay = strategy.getNextDelay();
        assertThat(delay).isLessThanOrEqualTo(Duration.ofMinutes(5));
    }
}