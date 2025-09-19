package com.marmitt.config.core;

import com.marmitt.core.application.usecase.*;
import com.marmitt.core.ports.inbound.listener.ManageListenersPort;
import com.marmitt.core.ports.inbound.message.ProcessMessagePort;
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
    public ProcessMessagePort processMessage(MessageProcessorRepositoryPort processorRepository,
                                           ListenerRepositoryPort listenerRepository) {
        return new ProcessMessageUseCase(processorRepository, listenerRepository);
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
