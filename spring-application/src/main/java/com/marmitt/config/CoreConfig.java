package com.marmitt.config;

import com.marmitt.binance.processor.BinanceMessageProcessor;
import com.marmitt.core.application.usecase.ConnectWebSocketUseCase;
import com.marmitt.core.application.usecase.DisconnectWebSocketUseCase;
import com.marmitt.core.application.usecase.ManageListenersUseCase;
import com.marmitt.core.application.usecase.ProcessMessageUseCase;
import com.marmitt.core.application.usecase.StatusWebSocketUseCase;
import com.marmitt.core.ports.inbound.listener.ManageListenersPort;
import com.marmitt.core.ports.inbound.message.ProcessMessagePort;
import com.marmitt.core.ports.inbound.websocket.ConnectWebSocketPort;
import com.marmitt.core.ports.inbound.websocket.DisconnectWebSocketPort;
import com.marmitt.core.ports.inbound.websocket.StatusWebSocketPort;
import com.marmitt.core.ports.outbound.repository.ListenerRepositoryPort;
import com.marmitt.core.ports.outbound.repository.MessageProcessorRepositoryPort;
import com.marmitt.core.ports.outbound.websocket.AdapterMessageProcessorPort;
import com.marmitt.repository.InMemoryListenerRepository;
import com.marmitt.repository.InMemoryMessageProcessorRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CoreConfig {

    @Bean
    public ConnectWebSocketPort connectWebSocket() {
        return new ConnectWebSocketUseCase();
    }

    @Bean
    public DisconnectWebSocketPort disconnectWebSocket() {
        return new DisconnectWebSocketUseCase();
    }

    @Bean
    public StatusWebSocketPort statusWebSocket() {
        return new StatusWebSocketUseCase();
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

}
