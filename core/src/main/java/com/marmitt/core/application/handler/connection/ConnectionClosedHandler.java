package com.marmitt.core.application.handler.connection;

import com.marmitt.core.dto.connection.ConnectionKey;
import com.marmitt.core.dto.connection.ConnectionResultDto;
import com.marmitt.core.dto.events.WebSocketClosedEvent;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.ports.inbound.handler.ConnectionClosedPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConnectionClosedHandler implements ConnectionClosedPort {

    private final WebSocketConnectionRepositoryPort connectionManagerPort;

    public ConnectionClosedHandler(WebSocketConnectionRepositoryPort connectionManagerPort) {
        this.connectionManagerPort = connectionManagerPort;
    }

    @Override
    public void execute(final WebSocketClosedEvent event) {
        log.info("WebSocket closed - exchange={} channel={} code={} reason={}", event.exchange(), event.channel(), event.code(), event.reason());
        try {
            ConnectionKey key = new ConnectionKey(event.exchange(), event.channel());
            WebSocketConnectionManager manager = connectionManagerPort.getConnection(key);
            if (manager.getConnectionResult().isDisconnecting()) {
                manager.setConnectionResult(ConnectionResultDto.disconnected(
                        manager.getConnectionResult().message(), event.connectionId()));
            } else {
                manager.setConnectionResult(ConnectionResultDto.closed(event.code(), event.reason(), event.connectionId()));
            }
        } catch (Exception e) {
            log.error("Failed to process connection closed event - exchange={} channel={}", event.exchange(), event.channel(), e);
            throw new RuntimeException("Error processing connection closed event", e);
        }
    }
}
