package com.marmitt.ctrade.infrastructure.websocket;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Getter
@Component
@Slf4j
public class WebSocketCircuitBreaker {
    
    private static final int FAILURE_THRESHOLD = 5;
    private static final long TIMEOUT_MINUTES = 2;
    
    private State state = State.CLOSED;
    private int failureCount = 0;
    private LocalDateTime lastFailureTime;
    
    public enum State {
        CLOSED,    // Normal operation
        OPEN,      // Circuit breaker is open, requests fail fast
        HALF_OPEN  // Testing if service is back
    }
    
    public synchronized boolean canConnect() {
        return switch (state) {
            case CLOSED, HALF_OPEN -> true;
            case OPEN -> {
                if (shouldAttemptReset()) {
                    state = State.HALF_OPEN;
                    log.info("Circuit breaker moving to HALF_OPEN state");
                    yield true;
                }
                yield false;
            }
        };
    }
    
    public synchronized void recordSuccess() {
        failureCount = 0;
        lastFailureTime = null;
        
        if (state != State.CLOSED) {
            state = State.CLOSED;
            log.info("Circuit breaker reset to CLOSED state");
        }
    }
    
    public synchronized void recordFailure() {
        failureCount++;
        lastFailureTime = LocalDateTime.now();
        
        if (state == State.HALF_OPEN) {
            state = State.OPEN;
            log.warn("Circuit breaker moving back to OPEN state after failure in HALF_OPEN");
        } else if (failureCount >= FAILURE_THRESHOLD && state == State.CLOSED) {
            state = State.OPEN;
            log.error("Circuit breaker OPENED after {} failures", failureCount);
        }
        
        log.warn("Circuit breaker recorded failure {}/{} at {}", 
                failureCount, FAILURE_THRESHOLD, lastFailureTime);
    }
    
    private boolean shouldAttemptReset() {
        return lastFailureTime != null && 
               ChronoUnit.MINUTES.between(lastFailureTime, LocalDateTime.now()) >= TIMEOUT_MINUTES;
    }

    public boolean isOpen() {
        return state == State.OPEN;
    }
    
    public void reset() {
        state = State.CLOSED;
        failureCount = 0;
        lastFailureTime = null;
        log.info("Circuit breaker manually reset");
    }
}