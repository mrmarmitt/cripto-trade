package com.marmitt.core.application.handler.connection;

import com.marmitt.core.dto.connection.ConnectionKey;
import com.marmitt.core.dto.connection.ConnectionResultDto;
import com.marmitt.core.dto.events.WebSocketConnectedEvent;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.ports.inbound.handler.ConnectionEstablishedPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConnectionEstablishedHandler implements ConnectionEstablishedPort {

    private final WebSocketConnectionRepositoryPort connectionManagerPort;

    public ConnectionEstablishedHandler(WebSocketConnectionRepositoryPort connectionManagerPort) {
        this.connectionManagerPort = connectionManagerPort;
    }

    @Override
    public void execute(final WebSocketConnectedEvent event) {
        log.info("WebSocket connected - exchange={} channel={} connectionId={}", event.exchange(), event.channel(), event.connectionId());
        try {
            ConnectionKey key = new ConnectionKey(event.exchange(), event.channel());
            WebSocketConnectionManager manager = connectionManagerPort.getConnection(key);
            manager.setConnectionResult(ConnectionResultDto.connected(event.message(), event.connectionId()));
        } catch (Exception e) {
            log.error("Failed to process connection established event - exchange={} channel={}", event.exchange(), event.channel(), e);
            throw new RuntimeException("Error processing connection established event", e);
        }
    }
}
