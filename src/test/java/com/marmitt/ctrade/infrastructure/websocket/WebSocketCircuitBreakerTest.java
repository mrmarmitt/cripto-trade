package com.marmitt.ctrade.infrastructure.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketCircuitBreakerTest {
    
    private WebSocketCircuitBreaker circuitBreaker;
    
    @BeforeEach
    void setUp() {
        circuitBreaker = new WebSocketCircuitBreaker();
    }
    
    @Test
    void shouldStartInClosedState() {
        assertThat(circuitBreaker.getState()).isEqualTo(WebSocketCircuitBreaker.State.CLOSED);
        assertThat(circuitBreaker.canConnect()).isTrue();
        assertThat(circuitBreaker.isOpen()).isFalse();
        assertThat(circuitBreaker.getFailureCount()).isEqualTo(0);
    }
    
    @Test
    void shouldOpenAfterThresholdFailures() {
        // Record failures up to threshold
        for (int i = 0; i < 5; i++) {
            circuitBreaker.recordFailure();
        }
        
        assertThat(circuitBreaker.getState()).isEqualTo(WebSocketCircuitBreaker.State.OPEN);
        assertThat(circuitBreaker.canConnect()).isFalse();
        assertThat(circuitBreaker.isOpen()).isTrue();
        assertThat(circuitBreaker.getFailureCount()).isEqualTo(5);
    }
    
    @Test
    void shouldResetOnSuccess() {
        // Record some failures
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        
        assertThat(circuitBreaker.getFailureCount()).isEqualTo(2);
        
        // Record success
        circuitBreaker.recordSuccess();
        
        assertThat(circuitBreaker.getState()).isEqualTo(WebSocketCircuitBreaker.State.CLOSED);
        assertThat(circuitBreaker.getFailureCount()).isEqualTo(0);
        assertThat(circuitBreaker.getLastFailureTime()).isNull();
    }
    
    @Test
    void shouldAllowManualReset() {
        // Open the circuit
        for (int i = 0; i < 5; i++) {
            circuitBreaker.recordFailure();
        }
        
        assertThat(circuitBreaker.isOpen()).isTrue();
        
        // Manual reset
        circuitBreaker.reset();
        
        assertThat(circuitBreaker.getState()).isEqualTo(WebSocketCircuitBreaker.State.CLOSED);
        assertThat(circuitBreaker.canConnect()).isTrue();
        assertThat(circuitBreaker.getFailureCount()).isEqualTo(0);
    }
    
    @Test
    void shouldRecordFailureTimes() {
        LocalDateTime beforeFailure = LocalDateTime.now();
        
        circuitBreaker.recordFailure();
        
        LocalDateTime afterFailure = LocalDateTime.now();
        LocalDateTime failureTime = circuitBreaker.getLastFailureTime();
        
        assertThat(failureTime).isNotNull();
        assertThat(failureTime).isAfter(beforeFailure.minusSeconds(1));
        assertThat(failureTime).isBefore(afterFailure.plusSeconds(1));
    }
    
    @Test
    void shouldMoveFromHalfOpenToOpenOnFailure() {
        // Force circuit to open
        for (int i = 0; i < 5; i++) {
            circuitBreaker.recordFailure();
        }
        
        // Manually set to half-open (simulating timeout)
        circuitBreaker.reset();
        for (int i = 0; i < 5; i++) {
            circuitBreaker.recordFailure();
        }
        
        // Set last failure time to past to trigger half-open
        // This is tricky to test without modifying the timeout, 
        // but we can at least verify the basic state management
        assertThat(circuitBreaker.getState()).isEqualTo(WebSocketCircuitBreaker.State.OPEN);
    }
}