package com.marmitt.core.application.usecase.handler.connection;

import com.marmitt.core.dto.connection.ConnectionResultDto;
import com.marmitt.core.dto.events.WebSocketConnectedEvent;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.ports.inbound.handler.ConnectionEstablishedPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import lombok.extern.slf4j.Slf4j;

/**
 * Use case para processar eventos de conexão WebSocket estabelecida.
 * <p>
 * Responsável por:
 * - Atualizar o estado da conexão para CONNECTED
 * - Validar a transição de estado
 * - Logging de eventos de conexão
 */
@Slf4j
public class HandleConnectionEstablishedUseCase implements ConnectionEstablishedPort {

    private final WebSocketConnectionRepositoryPort connectionManagerPort;

    public HandleConnectionEstablishedUseCase(WebSocketConnectionRepositoryPort connectionManagerPort) {
        this.connectionManagerPort = connectionManagerPort;
    }

    @Override
    public void execute(final WebSocketConnectedEvent event) {
        log.info("Processing connection established event for exchange: {}", event.exchange());

        try {
            WebSocketConnectionManager manager = connectionManagerPort.getConnection(event.exchange());
            manager.setConnectionResult(ConnectionResultDto.connected(
                            event.message(),
                            event.connectionId()
                    )
            );

            log.info("Successfully updated connection state to CONNECTED for exchange: {}", event.exchange());

        } catch (Exception e) {
            log.error("Failed to process connection established event for exchange: {}", event.exchange(), e);
            throw new RuntimeException("Error processing connection established event", e);
        }
    }
}