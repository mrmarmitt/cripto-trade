package com.marmitt.core.application.usecase.handler.connection;

import com.marmitt.core.dto.connection.ConnectionResultDto;
import com.marmitt.core.dto.events.WebSocketFailedEvent;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.ports.inbound.handler.ConnectionFailedPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HandleConnectionFailedUseCase implements ConnectionFailedPort {

    private final WebSocketConnectionRepositoryPort connectionManagerPort;

    public HandleConnectionFailedUseCase(WebSocketConnectionRepositoryPort connectionManagerPort) {
        this.connectionManagerPort = connectionManagerPort;
    }

    @Override
    public void execute(final WebSocketFailedEvent event) {
        log.warn("Processing connection failed event for exchange: {} - Reason: {}", 
                event.exchange(), event.reason());
        
        try {
            ConnectionResultDto connectionResult = event.cause() != null ?
                    ConnectionResultDto.failure(event.reason(), event.connectionId(), event.cause()) :
                    ConnectionResultDto.failure(this.getClass().getSimpleName(), event.reason());

            WebSocketConnectionManager manager = connectionManagerPort.getConnection(event.exchange());
            manager.setConnectionResult(connectionResult);

            log.info("Successfully updated connection state to ERROR for exchange: {}", event.exchange());
            
        } catch (Exception e) {
            log.error("Failed to process connection failed event for exchange: {}", event.exchange(), e);
            throw new RuntimeException("Error processing connection failed event", e);
        }
    }
}