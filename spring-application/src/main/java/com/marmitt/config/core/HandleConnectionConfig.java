package com.marmitt.config.core;

import com.marmitt.core.application.usecase.handler.connection.*;
import com.marmitt.core.ports.inbound.handler.*;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HandleConnectionConfig {

    @Bean
    public HandleConnectionEstablishedPort handleConnectionEstablished(WebSocketConnectionRepositoryPort connectionManager) {
        return new HandleConnectionEstablishedUseCase(connectionManager);
    }

    @Bean
    public HandleConnectionClosingPort handleConnectionClosing(WebSocketConnectionRepositoryPort connectionManager) {
        return new HandleConnectionClosingUseCase(connectionManager);
    }

    @Bean
    public HandleConnectionClosedPort handleConnectionClosed(WebSocketConnectionRepositoryPort connectionManager) {
        return new HandleConnectionClosedUseCase(connectionManager);
    }

    @Bean
    public HandleConnectionDisconnectedPort handleConnectionDisconnected(WebSocketConnectionRepositoryPort connectionManager) {
        return new HandleConnectionDisconnectedUseCase(connectionManager);
    }

    @Bean
    public HandleConnectionFailedPort handleConnectionFailed(WebSocketConnectionRepositoryPort connectionManager) {
        return new HandleConnectionFailedUseCase(connectionManager);
    }
}
