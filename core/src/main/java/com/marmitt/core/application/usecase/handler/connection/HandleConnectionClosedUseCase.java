package com.marmitt.core.application.usecase.handler.connection;

import com.marmitt.core.domain.ConnectionResult;
import com.marmitt.core.dto.events.WebSocketClosedEvent;
import com.marmitt.core.dto.websocket.WebSocketConnectionManager;
import com.marmitt.core.ports.inbound.handler.HandleConnectionClosedPort;
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
public class HandleConnectionClosedUseCase implements HandleConnectionClosedPort {

    private final WebSocketConnectionRepositoryPort connectionManagerPort;

    public HandleConnectionClosedUseCase(WebSocketConnectionRepositoryPort connectionManagerPort) {
        this.connectionManagerPort = connectionManagerPort;
    }

    @Override
    public void execute(WebSocketClosedEvent event) {
        log.info("Processing connection closed event for exchange: {} - Code: {}, Reason: {}",
                event.exchange(), event.code(), event.reason());

        try {
            WebSocketConnectionManager manager = connectionManagerPort.getConnection(event.exchange());

            if (manager.getConnectionResult().isDisconnecting()) {
                ConnectionResult connectionResult = manager.getConnectionResult();
                manager.setConnectionResult(ConnectionResult.disconnected(
                        connectionResult.message(),
                        event.connectionId())
                );
            } else {
                manager.setConnectionResult(ConnectionResult.closed(
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