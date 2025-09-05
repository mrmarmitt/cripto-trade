package com.marmitt.config;

import com.marmitt.core.application.usecase.ConnectWebSocketUseCase;
import com.marmitt.core.application.usecase.DisconnectWebSocketUseCase;
import com.marmitt.core.application.usecase.StatusWebSocketUseCase;
import com.marmitt.core.ports.inbound.websocket.ConnectWebSocketPort;
import com.marmitt.core.ports.inbound.websocket.DisconnectWebSocketPort;
import com.marmitt.core.ports.inbound.websocket.StatusWebSocketPort;
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

}
