package com.marmitt.application.spring.config.core;

import com.marmitt.core.application.usecase.*;
import com.marmitt.core.application.usecase.websocket.*;
import com.marmitt.core.ports.inbound.listener.ManageListenersPort;
import com.marmitt.core.ports.inbound.websocket.*;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.ListenerRepositoryPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPortRegistryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebSocketConfig {

    @Bean
    public ConnectMarketStreamPort connectMarketStream(WebSocketConnectionRepositoryPort connectionRepository,
                                                       ExchangeAdapterRepositoryPort adapterRepository,
                                                       WebSocketPortRegistryPort webSocketRegistry) {
        return new ConnectMarketStreamUseCase(connectionRepository, adapterRepository, webSocketRegistry);
    }

    @Bean
    public ConnectUserStreamPort connectUserStream(WebSocketConnectionRepositoryPort connectionRepository,
                                                   ExchangeAdapterRepositoryPort adapterRepository,
                                                   WebSocketPortRegistryPort webSocketRegistry) {
        return new ConnectUserStreamUseCase(connectionRepository, adapterRepository, webSocketRegistry);
    }

    @Bean
    public DisconnectWebSocketPort disconnectWebSocket(WebSocketConnectionRepositoryPort connectionRepository,
                                                       ExchangeAdapterRepositoryPort adapterRepository,
                                                       WebSocketPortRegistryPort webSocketRegistry) {
        return new DisconnectWebSocketUseCase(connectionRepository, adapterRepository, webSocketRegistry);
    }

    @Bean
    public SendMessageWebSocketPort sendMessageWebSocket(WebSocketConnectionRepositoryPort connectionRepository,
                                                         ExchangeAdapterRepositoryPort adapterRepository,
                                                         WebSocketPortRegistryPort webSocketRegistry) {
        return new SendMessageWebSocketUseCase(connectionRepository, adapterRepository, webSocketRegistry);
    }
    
    @Bean
    public ManageListenersPort manageListeners(ListenerRepositoryPort listenerRepository) {
        return new ManageListenersUseCase(listenerRepository);
    }

    @Bean
    public ConnectStatusWebSocketPort connectStatusWebSocket(WebSocketConnectionRepositoryPort webSocketConnectionRepository){
        return new ConnectStatusWebSocketUseCase(webSocketConnectionRepository);
    }

    @Bean
    public ConnectStatsWebSocketPort connectStatsWebSocket(WebSocketConnectionRepositoryPort webSocketConnectionRepository){
        return new ConnectStatsWebSocketUseCase(webSocketConnectionRepository);
    }
}
