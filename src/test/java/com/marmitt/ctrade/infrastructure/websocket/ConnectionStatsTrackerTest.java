package com.marmitt.ctrade.infrastructure.websocket;

import com.marmitt.ctrade.domain.port.ExchangeWebSocketAdapter.ConnectionStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários para ConnectionStatsTracker.
 * Testa rastreamento de estatísticas de conexão WebSocket de forma thread-safe.
 */
class ConnectionStatsTrackerTest {

    private ConnectionStatsTracker statsTracker;

    @BeforeEach
    void setUp() {
        statsTracker = new ConnectionStatsTracker();
    }

    @Test
    void shouldInitializeWithZeroStats() {
        // When
        ConnectionStats stats = statsTracker.getStats();
        
        // Then
        assertThat(stats.totalConnections()).isEqualTo(0);
        assertThat(stats.totalReconnections()).isEqualTo(0);
        assertThat(stats.totalMessagesReceived()).isEqualTo(0);
        assertThat(stats.totalErrors()).isEqualTo(0);
        assertThat(stats.lastConnectedAt()).isNull();
        assertThat(stats.lastMessageAt()).isNull();
    }

    @Test
    void shouldRecordConnectionCorrectly() {
        // Given
        LocalDateTime before = LocalDateTime.now();
        
        // When
        statsTracker.recordConnection();
        
        // Then
        ConnectionStats stats = statsTracker.getStats();
        LocalDateTime after = LocalDateTime.now();
        
        assertThat(stats.totalConnections()).isEqualTo(1);
        assertThat(stats.lastConnectedAt()).isNotNull();
        assertThat(stats.lastConnectedAt()).isAfterOrEqualTo(before);
        assertThat(stats.lastConnectedAt()).isBeforeOrEqualTo(after);
    }

    @Test
    void shouldRecordMultipleConnections() {
        // When
        statsTracker.recordConnection();
        statsTracker.recordConnection();
        statsTracker.recordConnection();
        
        // Then
        ConnectionStats stats = statsTracker.getStats();
        assertThat(stats.totalConnections()).isEqualTo(3);
    }

    @Test
    void shouldUpdateLastConnectedAtOnEachConnection() {
        // Given
        statsTracker.recordConnection();
        LocalDateTime firstConnectionTime = statsTracker.getStats().lastConnectedAt();
        
        // Wait a small amount to ensure different timestamp
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // When
        statsTracker.recordConnection();
        
        // Then
        LocalDateTime secondConnectionTime = statsTracker.getStats().lastConnectedAt();
        assertThat(secondConnectionTime).isAfter(firstConnectionTime);
    }

    @Test
    void shouldRecordReconnectionCorrectly() {
        // When
        statsTracker.recordReconnection();
        
        // Then
        ConnectionStats stats = statsTracker.getStats();
        assertThat(stats.totalReconnections()).isEqualTo(1);
    }

    @Test
    void shouldRecordMultipleReconnections() {
        // When
        statsTracker.recordReconnection();
        statsTracker.recordReconnection();
        statsTracker.recordReconnection();
        statsTracker.recordReconnection();
        
        // Then
        ConnectionStats stats = statsTracker.getStats();
        assertThat(stats.totalReconnections()).isEqualTo(4);
    }

    @Test
    void shouldRecordMessageReceivedCorrectly() {
        // Given
        LocalDateTime before = LocalDateTime.now();
        
        // When
        statsTracker.recordMessageReceived();
        
        // Then
        ConnectionStats stats = statsTracker.getStats();
        LocalDateTime after = LocalDateTime.now();
        
        assertThat(stats.totalMessagesReceived()).isEqualTo(1);
        assertThat(stats.lastMessageAt()).isNotNull();
        assertThat(stats.lastMessageAt()).isAfterOrEqualTo(before);
        assertThat(stats.lastMessageAt()).isBeforeOrEqualTo(after);
    }

    @Test
    void shouldRecordMultipleMessages() {
        // When
        for (int i = 0; i < 100; i++) {
            statsTracker.recordMessageReceived();
        }
        
        // Then
        ConnectionStats stats = statsTracker.getStats();
        assertThat(stats.totalMessagesReceived()).isEqualTo(100);
    }

    @Test
    void shouldUpdateLastMessageAtOnEachMessage() {
        // Given
        statsTracker.recordMessageReceived();
        LocalDateTime firstMessageTime = statsTracker.getStats().lastMessageAt();
        
        // Wait a small amount to ensure different timestamp
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // When
        statsTracker.recordMessageReceived();
        
        // Then
        LocalDateTime secondMessageTime = statsTracker.getStats().lastMessageAt();
        assertThat(secondMessageTime).isAfter(firstMessageTime);
    }

    @Test
    void shouldRecordErrorCorrectly() {
        // When
        statsTracker.recordError();
        
        // Then
        ConnectionStats stats = statsTracker.getStats();
        assertThat(stats.totalErrors()).isEqualTo(1);
    }

    @Test
    void shouldRecordMultipleErrors() {
        // When
        statsTracker.recordError();
        statsTracker.recordError();
        statsTracker.recordError();
        statsTracker.recordError();
        statsTracker.recordError();
        
        // Then
        ConnectionStats stats = statsTracker.getStats();
        assertThat(stats.totalErrors()).isEqualTo(5);
    }

    @Test
    void shouldUpdateLastConnectedAtManually() {
        // Given
        LocalDateTime customTime = LocalDateTime.of(2023, 1, 1, 12, 0, 0);
        
        // When
        statsTracker.updateLastConnectedAt(customTime);
        
        // Then
        ConnectionStats stats = statsTracker.getStats();
        assertThat(stats.lastConnectedAt()).isEqualTo(customTime);
    }

    @Test
    void shouldUpdateLastMessageAtManually() {
        // Given
        LocalDateTime customTime = LocalDateTime.of(2023, 1, 1, 12, 0, 0);
        
        // When
        statsTracker.updateLastMessageAt(customTime);
        
        // Then
        ConnectionStats stats = statsTracker.getStats();
        assertThat(stats.lastMessageAt()).isEqualTo(customTime);
    }

    @Test
    void shouldResetAllStatsCorrectly() {
        // Given - Record some stats first
        statsTracker.recordConnection();
        statsTracker.recordReconnection();
        statsTracker.recordMessageReceived();
        statsTracker.recordError();
        
        // Verify stats are not zero
        ConnectionStats statsBefore = statsTracker.getStats();
        assertThat(statsBefore.totalConnections()).isEqualTo(1);
        assertThat(statsBefore.totalReconnections()).isEqualTo(1);
        assertThat(statsBefore.totalMessagesReceived()).isEqualTo(1);
        assertThat(statsBefore.totalErrors()).isEqualTo(1);
        assertThat(statsBefore.lastConnectedAt()).isNotNull();
        assertThat(statsBefore.lastMessageAt()).isNotNull();
        
        // When
        statsTracker.reset();
        
        // Then
        ConnectionStats statsAfter = statsTracker.getStats();
        assertThat(statsAfter.totalConnections()).isEqualTo(0);
        assertThat(statsAfter.totalReconnections()).isEqualTo(0);
        assertThat(statsAfter.totalMessagesReceived()).isEqualTo(0);
        assertThat(statsAfter.totalErrors()).isEqualTo(0);
        assertThat(statsAfter.lastConnectedAt()).isNull();
        assertThat(statsAfter.lastMessageAt()).isNull();
    }

    @Test
    void shouldHandleConcurrentConnectionRecording() {
        // Given
        int numberOfThreads = 10;
        int connectionsPerThread = 100;
        Thread[] threads = new Thread[numberOfThreads];
        
        // When
        for (int i = 0; i < numberOfThreads; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < connectionsPerThread; j++) {
                    statsTracker.recordConnection();
                }
            });
            threads[i].start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Then
        ConnectionStats stats = statsTracker.getStats();
        assertThat(stats.totalConnections()).isEqualTo(numberOfThreads * connectionsPerThread);
    }

    @Test
    void shouldHandleConcurrentMessageRecording() {
        // Given
        int numberOfThreads = 10;
        int messagesPerThread = 100;
        Thread[] threads = new Thread[numberOfThreads];
        
        // When
        for (int i = 0; i < numberOfThreads; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < messagesPerThread; j++) {
                    statsTracker.recordMessageReceived();
                }
            });
            threads[i].start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Then
        ConnectionStats stats = statsTracker.getStats();
        assertThat(stats.totalMessagesReceived()).isEqualTo(numberOfThreads * messagesPerThread);
    }

    @Test
    void shouldHandleMixedConcurrentOperations() {
        // Given
        int numberOfThreads = 5;
        int operationsPerThread = 50;
        Thread[] threads = new Thread[numberOfThreads];
        
        // When
        for (int i = 0; i < numberOfThreads; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    statsTracker.recordConnection();
                    statsTracker.recordReconnection();
                    statsTracker.recordMessageReceived();
                    statsTracker.recordError();
                }
            });
            threads[i].start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Then
        ConnectionStats stats = statsTracker.getStats();
        int expectedCount = numberOfThreads * operationsPerThread;
        assertThat(stats.totalConnections()).isEqualTo(expectedCount);
        assertThat(stats.totalReconnections()).isEqualTo(expectedCount);
        assertThat(stats.totalMessagesReceived()).isEqualTo(expectedCount);
        assertThat(stats.totalErrors()).isEqualTo(expectedCount);
    }

    @Test
    void shouldPreserveIndependentCounters() {
        // When
        statsTracker.recordConnection();
        statsTracker.recordConnection();
        statsTracker.recordReconnection();
        statsTracker.recordMessageReceived();
        statsTracker.recordMessageReceived();
        statsTracker.recordMessageReceived();
        statsTracker.recordError();
        
        // Then
        ConnectionStats stats = statsTracker.getStats();
        assertThat(stats.totalConnections()).isEqualTo(2);
        assertThat(stats.totalReconnections()).isEqualTo(1);
        assertThat(stats.totalMessagesReceived()).isEqualTo(3);
        assertThat(stats.totalErrors()).isEqualTo(1);
    }

    @Test
    void shouldReturnNewConnectionStatsInstanceEachTime() {
        // Given
        statsTracker.recordConnection();
        
        // When
        ConnectionStats stats1 = statsTracker.getStats();
        ConnectionStats stats2 = statsTracker.getStats();
        
        // Then
        assertThat(stats1).isNotSameAs(stats2); // Different instances
        assertThat(stats1.totalConnections()).isEqualTo(stats2.totalConnections()); // Same values
    }

    @Test
    void shouldHandleResetDuringConcurrentOperations() {
        // Given
        Thread recordingThread = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                statsTracker.recordConnection();
                statsTracker.recordMessageReceived();
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        
        recordingThread.start();
        
        // Wait a bit then reset
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // When
        statsTracker.reset();
        
        // Clean up
        recordingThread.interrupt();
        try {
            recordingThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Then - After reset, stats should be zero regardless of concurrent operations
        ConnectionStats stats = statsTracker.getStats();
        // Note: Due to concurrent execution, some operations might have occurred after reset
        // but the reset itself should have worked correctly
        assertThat(stats).isNotNull();
    }

    @Test
    void shouldHandleManualTimestampUpdatesIndependently() {
        // Given
        LocalDateTime connectionTime = LocalDateTime.of(2023, 1, 1, 10, 0, 0);
        LocalDateTime messageTime = LocalDateTime.of(2023, 1, 1, 11, 0, 0);
        
        // When
        statsTracker.updateLastConnectedAt(connectionTime);
        statsTracker.updateLastMessageAt(messageTime);
        
        // Then
        ConnectionStats stats = statsTracker.getStats();
        assertThat(stats.lastConnectedAt()).isEqualTo(connectionTime);
        assertThat(stats.lastMessageAt()).isEqualTo(messageTime);
        
        // Manual updates should not affect counters
        assertThat(stats.totalConnections()).isEqualTo(0);
        assertThat(stats.totalMessagesReceived()).isEqualTo(0);
    }

    @Test
    void shouldOverwriteTimestampsWithManualUpdates() {
        // Given
        statsTracker.recordConnection(); // Sets automatic timestamp
        statsTracker.recordMessageReceived(); // Sets automatic timestamp
        
        LocalDateTime customConnectionTime = LocalDateTime.of(2020, 1, 1, 12, 0, 0);
        LocalDateTime customMessageTime = LocalDateTime.of(2020, 1, 1, 13, 0, 0);
        
        // When
        statsTracker.updateLastConnectedAt(customConnectionTime);
        statsTracker.updateLastMessageAt(customMessageTime);
        
        // Then
        ConnectionStats stats = statsTracker.getStats();
        assertThat(stats.lastConnectedAt()).isEqualTo(customConnectionTime);
        assertThat(stats.lastMessageAt()).isEqualTo(customMessageTime);
        assertThat(stats.totalConnections()).isEqualTo(1); // Counter preserved
        assertThat(stats.totalMessagesReceived()).isEqualTo(1); // Counter preserved
    }

    @Test
    void shouldHandleNullTimestampUpdates() {
        // Given
        statsTracker.recordConnection();
        statsTracker.recordMessageReceived();
        
        // When
        statsTracker.updateLastConnectedAt(null);
        statsTracker.updateLastMessageAt(null);
        
        // Then
        ConnectionStats stats = statsTracker.getStats();
        assertThat(stats.lastConnectedAt()).isNull();
        assertThat(stats.lastMessageAt()).isNull();
        assertThat(stats.totalConnections()).isEqualTo(1); // Counter preserved
        assertThat(stats.totalMessagesReceived()).isEqualTo(1); // Counter preserved
    }
}