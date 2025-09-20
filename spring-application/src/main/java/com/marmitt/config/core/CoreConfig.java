package com.marmitt.config.core;

import com.marmitt.core.application.usecase.*;
import com.marmitt.core.application.usecase.handler.HandlerProcessMessageUseCase;
import com.marmitt.core.application.usecase.websocket.ConnectStatsWebSocketUseCase;
import com.marmitt.core.application.usecase.websocket.ConnectStatusWebSocketUseCase;
import com.marmitt.core.application.usecase.websocket.ConnectWebSocketUseCase;
import com.marmitt.core.application.usecase.websocket.DisconnectWebSocketUseCase;
import com.marmitt.core.ports.inbound.listener.ManageListenersPort;
import com.marmitt.core.ports.inbound.handler.HandlerProcessMessagePort;
import com.marmitt.core.ports.inbound.websocket.ConnectStatsWebSocketPort;
import com.marmitt.core.ports.inbound.websocket.ConnectStatusWebSocketPort;
import com.marmitt.core.ports.inbound.websocket.ConnectWebSocketPort;
import com.marmitt.core.ports.inbound.websocket.DisconnectWebSocketPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.ListenerRepositoryPort;
import com.marmitt.core.ports.outbound.repository.MessageProcessorRepositoryPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CoreConfig {

    @Bean
    public ConnectWebSocketPort connectWebSocket(WebSocketConnectionRepositoryPort connectionRepository,
                                                 ExchangeAdapterRepositoryPort adapterRepository) {
        return new ConnectWebSocketUseCase(connectionRepository, adapterRepository);
    }

    @Bean
    public DisconnectWebSocketPort disconnectWebSocket(WebSocketConnectionRepositoryPort connectionRepository,
                                                       ExchangeAdapterRepositoryPort adapterRepository) {
        return new DisconnectWebSocketUseCase(connectionRepository, adapterRepository);
    }
    
    @Bean
    public ManageListenersPort manageListeners(ListenerRepositoryPort listenerRepository) {
        return new ManageListenersUseCase(listenerRepository);
    }

    @Bean
    public HandlerProcessMessagePort processMessage(MessageProcessorRepositoryPort processorRepository,
                                                    ListenerRepositoryPort listenerRepository) {
        return new HandlerProcessMessageUseCase(processorRepository, listenerRepository);
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
