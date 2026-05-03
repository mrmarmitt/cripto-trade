package com.marmitt.core.application.handler.connection;

import com.marmitt.core.dto.connection.ConnectionKey;
import com.marmitt.core.dto.connection.ConnectionResultDto;
import com.marmitt.core.dto.events.WebSocketFailedEvent;
import com.marmitt.core.dto.websocket.request.MessageRequest;
import com.marmitt.core.dto.websocket.request.StreamSubscriptionRequest;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.enums.StreamChannel;
import com.marmitt.core.ports.inbound.handler.ConnectionFailedPort;
import com.marmitt.core.ports.inbound.websocket.ConnectMarketStreamPort;
import com.marmitt.core.ports.inbound.websocket.ConnectUserStreamPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ConnectionFailedHandler implements ConnectionFailedPort {

    private static final long RECONNECT_DELAY_SECONDS = 5;
    private static final int MAX_RECONNECT_ATTEMPTS = 10;

    private final WebSocketConnectionRepositoryPort connectionRepository;
    private final ConnectMarketStreamPort connectMarketStreamPort;
    private final ConnectUserStreamPort connectUserStreamPort;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public ConnectionFailedHandler(WebSocketConnectionRepositoryPort connectionRepository,
                                   ConnectMarketStreamPort connectMarketStreamPort,
                                   ConnectUserStreamPort connectUserStreamPort) {
        this.connectionRepository = connectionRepository;
        this.connectMarketStreamPort = connectMarketStreamPort;
        this.connectUserStreamPort = connectUserStreamPort;
    }

    @Override
    public void execute(final WebSocketFailedEvent event) {
        log.warn("WebSocket failed - exchange={} channel={} reason={}", event.exchange(), event.channel(), event.reason());
        try {
            ConnectionKey key = new ConnectionKey(event.exchange(), event.channel());
            WebSocketConnectionManager manager = connectionRepository.getConnection(key);

            ConnectionResultDto failure = event.cause() != null
                    ? ConnectionResultDto.failure(event.reason(), event.connectionId(), event.cause())
                    : ConnectionResultDto.failure(this.getClass().getSimpleName(), event.reason());
            manager.setConnectionResult(failure);

            scheduleReconnect(event.exchange(), event.channel(), manager, 1);

        } catch (Exception e) {
            log.error("Failed to process connection failed event - exchange={} channel={}", event.exchange(), event.channel(), e);
            throw new RuntimeException("Error processing connection failed event", e);
        }
    }

    private void scheduleReconnect(String exchangeName, StreamChannel channel, WebSocketConnectionManager manager, int attempt) {
        if (attempt > MAX_RECONNECT_ATTEMPTS) {
            log.error("Max reconnect attempts ({}) reached - exchange={} channel={}", MAX_RECONNECT_ATTEMPTS, exchangeName, channel);
            return;
        }

        long delay = RECONNECT_DELAY_SECONDS * attempt;
        log.info("Scheduling reconnect attempt {}/{} - exchange={} channel={} in {}s",
                attempt, MAX_RECONNECT_ATTEMPTS, exchangeName, channel, delay);

        scheduler.schedule(() -> {
            try {
                if (channel == StreamChannel.MARKET) {
                    reconnectMarketStream(exchangeName, manager, attempt);
                } else {
                    reconnectUserStream(exchangeName, attempt);
                }
            } catch (Exception e) {
                log.error("Reconnect attempt {} failed - exchange={} channel={}: {}", attempt, exchangeName, channel, e.getMessage());
                scheduleReconnect(exchangeName, channel, manager, attempt + 1);
            }
        }, delay, TimeUnit.SECONDS);
    }

    private void reconnectMarketStream(String exchangeName, WebSocketConnectionManager manager, int attempt) {
        if (manager.getRequestHistory().isEmpty()) {
            log.warn("No request history for market reconnect - exchange={}", exchangeName);
            return;
        }
        MessageRequest lastRequest = manager.getLastRequestHistory();
        if (!(lastRequest instanceof StreamSubscriptionRequest subscriptionRequest)) {
            log.warn("Last request is not a StreamSubscriptionRequest - exchange={}", exchangeName);
            return;
        }
        log.info("Reconnecting market stream - exchange={} attempt={}", exchangeName, attempt);
        connectMarketStreamPort.execute(exchangeName, subscriptionRequest);
    }

    private void reconnectUserStream(String exchangeName, int attempt) {
        log.info("Reconnecting user data stream - exchange={} attempt={}", exchangeName, attempt);
        connectUserStreamPort.execute(exchangeName);
    }
}
