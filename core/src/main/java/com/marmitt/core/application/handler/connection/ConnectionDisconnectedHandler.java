package com.marmitt.core.application.handler.connection;

import com.marmitt.core.dto.connection.ConnectionKey;
import com.marmitt.core.dto.connection.ConnectionResultDto;
import com.marmitt.core.dto.events.WebSocketDisconnectedEvent;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.ports.inbound.handler.ConnectionDisconnectedPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConnectionDisconnectedHandler implements ConnectionDisconnectedPort {

    private final WebSocketConnectionRepositoryPort connectionManagerPort;

    public ConnectionDisconnectedHandler(WebSocketConnectionRepositoryPort connectionManagerPort) {
        this.connectionManagerPort = connectionManagerPort;
    }

    @Override
    public void execute(final WebSocketDisconnectedEvent event) {
        log.info("WebSocket disconnected - exchange={} channel={} reason={} manual={}", event.exchange(), event.channel(), event.reason(), event.wasManual());
        try {
            ConnectionKey key = new ConnectionKey(event.exchange(), event.channel());
            WebSocketConnectionManager manager = connectionManagerPort.getConnection(key);
            manager.setConnectionResult(ConnectionResultDto.disconnected(event.reason(), event.connectionId()));
        } catch (Exception e) {
            log.error("Failed to process disconnection event - exchange={} channel={}", event.exchange(), event.channel(), e);
            throw new RuntimeException("Error processing disconnection event", e);
        }
    }
}
