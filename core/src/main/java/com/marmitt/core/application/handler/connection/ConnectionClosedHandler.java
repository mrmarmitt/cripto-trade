package com.marmitt.core.application.handler.connection;

import com.marmitt.core.dto.connection.ConnectionResultDto;
import com.marmitt.core.dto.events.WebSocketClosedEvent;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.ports.inbound.handler.ConnectionClosedPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import lombok.extern.slf4j.Slf4j;

/**
 * Use case para processar eventos de conexão WebSocket fechada.
 * <p>
 * Responsável por:
 * - Atualizar o estado da conexão para CLOSED
 * - Registrar detalhes do fechamento
 * - Distinguir entre fechamentos esperados e inesperados
 */
@Slf4j
public class ConnectionClosedHandler implements ConnectionClosedPort {

    private final WebSocketConnectionRepositoryPort connectionManagerPort;

    public ConnectionClosedHandler(WebSocketConnectionRepositoryPort connectionManagerPort) {
        this.connectionManagerPort = connectionManagerPort;
    }

    @Override
    public void execute(final WebSocketClosedEvent event) {
        log.info("Processing connection closed event for exchange: {} - Code: {}, Reason: {}",
                event.exchange(), event.code(), event.reason());

        try {
            WebSocketConnectionManager manager = connectionManagerPort.getConnection(event.exchange());

            if (manager.getConnectionResult().isDisconnecting()) {
                ConnectionResultDto connectionResult = manager.getConnectionResult();
                manager.setConnectionResult(ConnectionResultDto.disconnected(
                        connectionResult.message(),
                        event.connectionId())
                );
            } else {
                manager.setConnectionResult(ConnectionResultDto.closed(
                        event.code(),
                        event.reason(),
                        event.connectionId())
                );
            }

            log.info("Successfully updated connection state to CLOSED for exchange: {}", event.exchange());

        } catch (Exception e) {
            log.error("Failed to process connection closed event for exchange: {}", event.exchange(), e);
            throw new RuntimeException("Error processing connection closed event", e);
        }
    }
}