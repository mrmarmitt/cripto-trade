package com.marmitt.core.application.handler.connection;

import com.marmitt.core.dto.connection.ConnectionResultDto;
import com.marmitt.core.dto.events.WebSocketFailedEvent;
import com.marmitt.core.dto.websocket.request.MessageRequest;
import com.marmitt.core.dto.websocket.request.StreamSubscriptionRequest;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.ports.inbound.handler.ConnectionFailedPort;
import com.marmitt.core.ports.inbound.websocket.ConnectWebSocketPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ConnectionFailedHandler implements ConnectionFailedPort {

    private static final long RECONNECT_DELAY_SECONDS = 5;
    private static final int MAX_RECONNECT_ATTEMPTS = 10;

    private final WebSocketConnectionRepositoryPort connectionManagerPort;
    private final ConnectWebSocketPort connectWebSocketPort;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public ConnectionFailedHandler(WebSocketConnectionRepositoryPort connectionManagerPort,
                                   ConnectWebSocketPort connectWebSocketPort) {
        this.connectionManagerPort = connectionManagerPort;
        this.connectWebSocketPort = connectWebSocketPort;
    }

    @Override
    public void execute(final WebSocketFailedEvent event) {
        log.warn("Processing connection failed event for exchange: {} - Reason: {}",
                event.exchange(), event.reason());

        try {
            WebSocketConnectionManager manager = connectionManagerPort.getConnection(event.exchange());

            ConnectionResultDto connectionResult = event.cause() != null ?
                    ConnectionResultDto.failure(event.reason(), event.connectionId(), event.cause()) :
                    ConnectionResultDto.failure(this.getClass().getSimpleName(), event.reason());

            manager.setConnectionResult(connectionResult);

            log.info("Successfully updated connection state to ERROR for exchange: {}", event.exchange());

            // Tentar reconectar automaticamente
            scheduleReconnect(event.exchange(), manager, 1);

        } catch (Exception e) {
            log.error("Failed to process connection failed event for exchange: {}", event.exchange(), e);
            throw new RuntimeException("Error processing connection failed event", e);
        }
    }

    private void scheduleReconnect(String exchangeName, WebSocketConnectionManager manager, int attempt) {
        if (attempt > MAX_RECONNECT_ATTEMPTS) {
            log.error("Max reconnect attempts ({}) reached for exchange: {}. Giving up.",
                    MAX_RECONNECT_ATTEMPTS, exchangeName);
            return;
        }

        if (manager.getRequestHistory().isEmpty()) {
            log.warn("No request history available for reconnection to exchange: {}", exchangeName);
            return;
        }

        long delay = RECONNECT_DELAY_SECONDS * attempt; // Exponential-ish backoff

        log.info("Scheduling reconnect attempt {}/{} for exchange: {} in {} seconds",
                attempt, MAX_RECONNECT_ATTEMPTS, exchangeName, delay);

        scheduler.schedule(() -> {
            try {
                MessageRequest lastRequest = manager.getLastRequestHistory();
                if (lastRequest instanceof StreamSubscriptionRequest subscriptionRequest) {
                    log.info("Attempting reconnection to exchange: {} (attempt {}/{})",
                            exchangeName, attempt, MAX_RECONNECT_ATTEMPTS);

                    connectWebSocketPort.execute(exchangeName, subscriptionRequest);

                    log.info("Reconnection initiated for exchange: {}", exchangeName);
                } else {
                    log.warn("Last request is not a StreamSubscriptionRequest, cannot reconnect");
                }
            } catch (Exception e) {
                log.error("Reconnection attempt {} failed for exchange: {} - {}",
                        attempt, exchangeName, e.getMessage());
                scheduleReconnect(exchangeName, manager, attempt + 1);
            }
        }, delay, TimeUnit.SECONDS);
    }
}