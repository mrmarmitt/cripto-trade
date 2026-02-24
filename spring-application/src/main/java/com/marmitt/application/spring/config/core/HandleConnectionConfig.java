package com.marmitt.application.spring.config.core;

import com.marmitt.core.application.usecase.handler.HandlerProcessMessageUseCase;
import com.marmitt.core.application.usecase.handler.connection.*;
import com.marmitt.core.ports.inbound.handler.*;
import com.marmitt.core.ports.inbound.websocket.ConnectWebSocketPort;
import com.marmitt.core.ports.inbound.websocket.PostConnectionEstablishedPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.ListenerRepositoryPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HandleConnectionConfig {

    @Bean
    public HandlerProcessMessagePort processMessage(WebSocketConnectionRepositoryPort connectionManager,
                                                    ExchangeAdapterRepositoryPort adapterRepository,
                                                    ListenerRepositoryPort listenerRepository) {
        return new HandlerProcessMessageUseCase(connectionManager, adapterRepository, listenerRepository);
    }

    @Bean
    public ConnectionEstablishedPort handleConnectionEstablished(WebSocketConnectionRepositoryPort connectionManager) {
        return new HandleConnectionEstablishedUseCase(connectionManager);
    }

    @Bean
    public PostConnectionEstablishedPort handlePostConnectionEstablished(WebSocketConnectionRepositoryPort connectionManager,
                                                                         ExchangeAdapterRepositoryPort adapterRepository) {
        return new HandlePostConnectionEstablishUseCase(connectionManager, adapterRepository);
    }

    @Bean
    public ConnectionClosingPort handleConnectionClosing(WebSocketConnectionRepositoryPort connectionManager) {
        return new HandleConnectionClosingUseCase(connectionManager);
    }

    @Bean
    public ConnectionClosedPort handleConnectionClosed(WebSocketConnectionRepositoryPort connectionManager) {
        return new HandleConnectionClosedUseCase(connectionManager);
    }

    @Bean
    public ConnectionDisconnectedPort handleConnectionDisconnected(WebSocketConnectionRepositoryPort connectionManager) {
        return new HandleConnectionDisconnectedUseCase(connectionManager);
    }

    @Bean
    public ConnectionFailedPort handleConnectionFailed(WebSocketConnectionRepositoryPort connectionManager,
                                                       ConnectWebSocketPort connectWebSocketPort) {
        return new HandleConnectionFailedUseCase(connectionManager, connectWebSocketPort);
    }
}
