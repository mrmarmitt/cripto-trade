package com.marmitt.core.application.handler.connection;

import com.marmitt.core.dto.connection.ConnectionResultDto;
import com.marmitt.core.dto.events.WebSocketClosingEvent;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.ports.inbound.handler.ConnectionClosingPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import lombok.extern.slf4j.Slf4j;

/**
 * Use case para processar eventos de início do fechamento da conexão WebSocket.
 * <p>
 * Responsável por:
 * - Atualizar o estado da conexão para CLOSING
 * - Registrar início do processo de fechamento
 * - Preparar para transição final para CLOSED
 */
@Slf4j
public class ConnectionClosingHandler implements ConnectionClosingPort {

    private final WebSocketConnectionRepositoryPort connectionManagerPort;

    public ConnectionClosingHandler(WebSocketConnectionRepositoryPort connectionManagerPort) {
        this.connectionManagerPort = connectionManagerPort;
    }

    @Override
    public void execute(final WebSocketClosingEvent event) {
        log.info("Processing connection closing event for exchange: {} - Code: {}, Reason: {}",
                event.exchange(), event.code(), event.reason());

        try {

            WebSocketConnectionManager manager = connectionManagerPort.getConnection(event.exchange());

            if (!manager.getConnectionResult().isDisconnecting()) {
                manager.setConnectionResult(ConnectionResultDto.closing(
                                event.code(),
                                event.reason(),
                                event.connectionId()
                        )
                );
            }

            log.info("Successfully updated connection state to CLOSING for exchange: {}", event.exchange());

        } catch (Exception e) {
            log.error("Failed to process connection closing event for exchange: {}", event.exchange(), e);
            throw new RuntimeException("Error processing connection closing event", e);
        }
    }
}