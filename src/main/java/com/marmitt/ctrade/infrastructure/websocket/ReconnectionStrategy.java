package com.marmitt.ctrade.infrastructure.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
@Slf4j
public class ReconnectionStrategy {
    
    private static final int MAX_ATTEMPTS = 10;
    private static final Duration MIN_DELAY = Duration.ofSeconds(1);
    private static final Duration MAX_DELAY = Duration.ofMinutes(5);
    
    private int currentAttempt = 0;
    private LocalDateTime lastAttempt;
    
    public boolean shouldReconnect() {
        return currentAttempt < MAX_ATTEMPTS;
    }
    
    public Duration getNextDelay() {
        if (currentAttempt == 0) {
            return MIN_DELAY;
        }
        
        // Exponential backoff: 1s, 2s, 4s, 8s, 16s, 32s, 64s, 128s, 256s, 300s (max)
        long delaySeconds = Math.min(
            (long) Math.pow(2, currentAttempt),
            MAX_DELAY.getSeconds()
        );
        
        Duration delay = Duration.ofSeconds(delaySeconds);
        log.info("Reconnection attempt {} scheduled in {}", currentAttempt + 1, delay);
        
        return delay;
    }
    
    public void recordAttempt() {
        currentAttempt++;
        lastAttempt = LocalDateTime.now();
        log.warn("Reconnection attempt {} of {} at {}", currentAttempt, MAX_ATTEMPTS, lastAttempt);
    }
    
    public void reset() {
        if (currentAttempt > 0) {
            log.info("Reconnection successful after {} attempts", currentAttempt);
        }
        currentAttempt = 0;
        lastAttempt = null;
    }
    
    public boolean isMaxAttemptsReached() {
        return currentAttempt >= MAX_ATTEMPTS;
    }
    
    public int getCurrentAttempt() {
        return currentAttempt;
    }
    
    public int getMaxAttempts() {
        return MAX_ATTEMPTS;
    }
    
    public LocalDateTime getLastAttempt() {
        return lastAttempt;
    }
}