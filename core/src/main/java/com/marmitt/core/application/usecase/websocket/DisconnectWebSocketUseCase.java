package com.marmitt.core.application.usecase.websocket;

import com.marmitt.core.dto.connection.ConnectionKey;
import com.marmitt.core.dto.connection.ConnectionResultDto;
import com.marmitt.core.dto.websocket.mapper.ConnectionResultMapper;
import com.marmitt.core.dto.websocket.response.WebSocketConnectionResponse;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.ports.inbound.websocket.DisconnectWebSocketPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeStreamingPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPortRegistryPort;

import java.util.Optional;
import java.util.UUID;

public class DisconnectWebSocketUseCase implements DisconnectWebSocketPort {

    private final WebSocketConnectionRepositoryPort connectionRepository;
    private final ExchangeAdapterRepositoryPort adapterRepository;
    private final WebSocketPortRegistryPort webSocketRegistry;

    public DisconnectWebSocketUseCase(WebSocketConnectionRepositoryPort connectionRepository,
                                      ExchangeAdapterRepositoryPort adapterRepository,
                                      WebSocketPortRegistryPort webSocketRegistry) {
        this.connectionRepository = connectionRepository;
        this.adapterRepository = adapterRepository;
        this.webSocketRegistry = webSocketRegistry;
    }

    @Override
    public WebSocketConnectionResponse execute(final String exchangeName) {
        WebSocketConnectionManager manager = connectionRepository.getConnection(ConnectionKey.market(exchangeName));

        Optional<ExchangeStreamingPort> streamingOptional = adapterRepository.findStreamingByName(exchangeName);
        if (streamingOptional.isEmpty()) {
            return ConnectionResultMapper.toResponse(
                    ConnectionResultDto.failure(this.getClass().getSimpleName(), "Exchange does not exist"),
                    exchangeName);
        }

        UUID connectionId = manager.getConnectionId();
        manager.setConnectionResult(ConnectionResultDto.disconnecting("Manual disconnection requested", connectionId));
        WebSocketPort webSocket = webSocketRegistry.findByExchangeName(exchangeName)
                .orElseThrow(() -> new IllegalStateException("No WebSocket registered for exchange: " + exchangeName));
        webSocket.disconnect(exchangeName, connectionId);

        adapterRepository.findUserStreamByName(exchangeName).ifPresent(userStream -> {
            WebSocketConnectionManager userManager = connectionRepository.getConnection(ConnectionKey.userStream(exchangeName));
            if (userManager != null) {
                UUID userConnectionId = userManager.getConnectionId();
                userManager.setConnectionResult(ConnectionResultDto.disconnecting("Manual disconnection requested", userConnectionId));
                userStream.onDisconnect(userConnectionId);
                webSocketRegistry.findUserStreamByExchangeName(exchangeName)
                        .ifPresent(ws -> ws.disconnect(exchangeName, userConnectionId));
            }
        });

        return ConnectionResultMapper.toResponse(manager.getConnectionResult(), exchangeName);
    }
}
