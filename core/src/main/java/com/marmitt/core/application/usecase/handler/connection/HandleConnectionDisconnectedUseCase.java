package com.marmitt.core.application.usecase.handler.connection;

import com.marmitt.core.domain.ConnectionResult;
import com.marmitt.core.dto.events.WebSocketDisconnectedEvent;
import com.marmitt.core.dto.websocket.WebSocketConnectionManager;
import com.marmitt.core.ports.inbound.handler.HandleConnectionDisconnectedPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import lombok.extern.slf4j.Slf4j;

/**
 * Use case para processar eventos de desconexão WebSocket.
 * <p>
 * Responsável por:
 * - Atualizar o estado da conexão para DISCONNECTED
 * - Distinguir entre desconexões manuais e automáticas
 * - Registrar detalhes da desconexão
 */
@Slf4j
public class HandleConnectionDisconnectedUseCase implements HandleConnectionDisconnectedPort {

    private final WebSocketConnectionRepositoryPort connectionManagerPort;

    public HandleConnectionDisconnectedUseCase(WebSocketConnectionRepositoryPort connectionManagerPort) {
        this.connectionManagerPort = connectionManagerPort;
    }

    @Override
    public void execute(WebSocketDisconnectedEvent event) {
        log.info("Processing disconnection event for exchange: {} - Reason: {} (Manual: {})",
                event.exchange(), event.reason(), event.wasManual());

        try {

            WebSocketConnectionManager manager = connectionManagerPort.getConnection(event.exchange());
            manager.setConnectionResult(ConnectionResult.disconnected(
                            event.reason(),
                            event.connectionId()
                    )
            );

            log.info("Successfully updated connection state to DISCONNECTED for exchange: {}", event.exchange());

        } catch (Exception e) {
            log.error("Failed to process disconnection event for exchange: {}", event.exchange(), e);
            throw new RuntimeException("Error processing disconnection event", e);
        }
    }
}