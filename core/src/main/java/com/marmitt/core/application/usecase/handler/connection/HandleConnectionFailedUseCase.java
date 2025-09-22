package com.marmitt.core.application.usecase.handler.connection;

import com.marmitt.core.domain.ConnectionResult;
import com.marmitt.core.dto.events.WebSocketFailedEvent;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.ports.inbound.handler.HandleConnectionFailedPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import lombok.extern.slf4j.Slf4j;

/**
 * Use case para processar eventos de falha na conexão WebSocket.
 * 
 * Responsável por:
 * - Atualizar o estado da conexão para ERROR
 * - Registrar detalhes da falha
 * - Logging de eventos de erro
 */
@Slf4j
public class HandleConnectionFailedUseCase implements HandleConnectionFailedPort {

    private final WebSocketConnectionRepositoryPort connectionManagerPort;

    public HandleConnectionFailedUseCase(WebSocketConnectionRepositoryPort connectionManagerPort) {
        this.connectionManagerPort = connectionManagerPort;
    }

    @Override
    public void execute(final WebSocketFailedEvent event) {
        log.warn("Processing connection failed event for exchange: {} - Reason: {}", 
                event.exchange(), event.reason());
        
        try {
            ConnectionResult connectionResult = event.cause() != null ?
                    ConnectionResult.failure(event.reason(), event.connectionId(), event.cause()) :
                    ConnectionResult.failure(event.reason());

            WebSocketConnectionManager manager = connectionManagerPort.getConnection(event.exchange());
            manager.setConnectionResult(connectionResult);

            log.info("Successfully updated connection state to ERROR for exchange: {}", event.exchange());
            
        } catch (Exception e) {
            log.error("Failed to process connection failed event for exchange: {}", event.exchange(), e);
            throw new RuntimeException("Error processing connection failed event", e);
        }
    }
}