package com.marmitt.core.application.handler.connection;

import com.marmitt.core.dto.connection.ConnectionKey;
import com.marmitt.core.dto.connection.ConnectionResultDto;
import com.marmitt.core.dto.events.WebSocketClosingEvent;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.ports.inbound.handler.ConnectionClosingPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConnectionClosingHandler implements ConnectionClosingPort {

    private final WebSocketConnectionRepositoryPort connectionManagerPort;

    public ConnectionClosingHandler(WebSocketConnectionRepositoryPort connectionManagerPort) {
        this.connectionManagerPort = connectionManagerPort;
    }

    @Override
    public void execute(final WebSocketClosingEvent event) {
        log.info("WebSocket closing - exchange={} channel={} code={} reason={}", event.exchange(), event.channel(), event.code(), event.reason());
        try {
            ConnectionKey key = new ConnectionKey(event.exchange(), event.channel());
            WebSocketConnectionManager manager = connectionManagerPort.getConnection(key);
            if (!manager.getConnectionResult().isDisconnecting()) {
                manager.setConnectionResult(ConnectionResultDto.closing(event.code(), event.reason(), event.connectionId()));
            }
        } catch (Exception e) {
            log.error("Failed to process connection closing event - exchange={} channel={}", event.exchange(), event.channel(), e);
            throw new RuntimeException("Error processing connection closing event", e);
        }
    }
}
