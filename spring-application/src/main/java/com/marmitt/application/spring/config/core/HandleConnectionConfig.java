package com.marmitt.application.spring.config.core;

import com.marmitt.core.application.handler.ProcessMessageHandler;
import com.marmitt.core.application.handler.ProcessUserMessageHandler;
import com.marmitt.core.application.handler.connection.*;
import com.marmitt.core.ports.inbound.handler.*;
import com.marmitt.core.ports.inbound.websocket.ConnectMarketStreamPort;
import com.marmitt.core.ports.inbound.websocket.ConnectUserStreamPort;
import com.marmitt.core.ports.inbound.websocket.PostConnectionEstablishedPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.ListenerRepositoryPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPortRegistryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HandleConnectionConfig {

    @Bean
    public HandlerProcessMessagePort processMessage(WebSocketConnectionRepositoryPort connectionManager,
                                                    ExchangeAdapterRepositoryPort adapterRepository,
                                                    ListenerRepositoryPort listenerRepository) {
        return new ProcessMessageHandler(connectionManager, adapterRepository, listenerRepository);
    }

    @Bean
    public HandlerProcessUserMessagePort processUserMessage(WebSocketConnectionRepositoryPort connectionManager,
                                                             ExchangeAdapterRepositoryPort adapterRepository,
                                                             ListenerRepositoryPort listenerRepository) {
        return new ProcessUserMessageHandler(connectionManager, adapterRepository, listenerRepository);
    }

    @Bean
    public ConnectionEstablishedPort handleConnectionEstablished(WebSocketConnectionRepositoryPort connectionManager) {
        return new ConnectionEstablishedHandler(connectionManager);
    }

    @Bean
    public PostConnectionEstablishedPort handlePostConnectionEstablished(WebSocketConnectionRepositoryPort connectionManager,
                                                                         ExchangeAdapterRepositoryPort adapterRepository,
                                                                         WebSocketPortRegistryPort webSocketRegistry) {
        return new PostConnectionEstablishHandler(connectionManager, adapterRepository, webSocketRegistry);
    }

    @Bean
    public ConnectionClosingPort handleConnectionClosing(WebSocketConnectionRepositoryPort connectionManager) {
        return new ConnectionClosingHandler(connectionManager);
    }

    @Bean
    public ConnectionClosedPort handleConnectionClosed(WebSocketConnectionRepositoryPort connectionManager) {
        return new ConnectionClosedHandler(connectionManager);
    }

    @Bean
    public ConnectionDisconnectedPort handleConnectionDisconnected(WebSocketConnectionRepositoryPort connectionManager) {
        return new ConnectionDisconnectedHandler(connectionManager);
    }

    @Bean
    public ConnectionFailedPort handleConnectionFailed(WebSocketConnectionRepositoryPort connectionManager,
                                                       ConnectMarketStreamPort connectMarketStreamPort,
                                                       ConnectUserStreamPort connectUserStreamPort) {
        return new ConnectionFailedHandler(connectionManager, connectMarketStreamPort, connectUserStreamPort);
    }
}
