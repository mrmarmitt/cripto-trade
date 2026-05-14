package com.marmitt.core.application.usecase.websocket;

import com.marmitt.core.dto.connection.ConnectionKey;
import com.marmitt.core.dto.connection.ConnectionResultDto;
import com.marmitt.core.dto.websocket.mapper.ConnectionResultMapper;
import com.marmitt.core.dto.websocket.response.WebSocketConnectionResponse;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.enums.ConnectionStatus;
import com.marmitt.core.ports.inbound.websocket.ConnectUserStreamPort;
import com.marmitt.core.ports.outbound.exchange.streaming.UserStreamSessionPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPortRegistryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

public class ConnectUserStreamUseCase implements ConnectUserStreamPort {

    private static final Logger log = LoggerFactory.getLogger(ConnectUserStreamUseCase.class);

    private final WebSocketConnectionRepositoryPort connectionRepository;
    private final ExchangeAdapterRepositoryPort adapterRepository;
    private final WebSocketPortRegistryPort webSocketRegistry;

    public ConnectUserStreamUseCase(WebSocketConnectionRepositoryPort connectionRepository,
                                    ExchangeAdapterRepositoryPort adapterRepository,
                                    WebSocketPortRegistryPort webSocketRegistry) {
        this.connectionRepository = connectionRepository;
        this.adapterRepository = adapterRepository;
        this.webSocketRegistry = webSocketRegistry;
    }

    @Override
    public Optional<WebSocketConnectionResponse> execute(String exchangeName) {
        if (adapterRepository.findUserStreamSessionByName(exchangeName).isEmpty()
                || adapterRepository.findUserStreamByName(exchangeName).isEmpty()) {
            return Optional.empty();
        }

        ConnectionKey userKey = ConnectionKey.userStream(exchangeName);
        connectionRepository.registerConnection(userKey);
        WebSocketConnectionManager manager = connectionRepository.getConnection(userKey);

        ConnectionStatus status = manager.getConnectionResult().status();
        if (status == ConnectionStatus.CONNECTED || status == ConnectionStatus.CONNECTING
                || status == ConnectionStatus.RECONNECTING
                || status == ConnectionStatus.DISCONNECTING || status == ConnectionStatus.CLOSING) {
            log.debug("User stream already {} - skipping connect - exchange={}", status, exchangeName);
            return Optional.of(ConnectionResultMapper.toResponse(manager.getConnectionResult(), exchangeName));
        }

        boolean isReconnect = status == ConnectionStatus.ERROR || status == ConnectionStatus.CLOSED;
        manager.setConnectionResult(isReconnect
                ? ConnectionResultDto.reconnecting(1, 1)
                : ConnectionResultDto.connecting());

        UserStreamSessionPort session = adapterRepository.findUserStreamSessionByName(exchangeName).orElseThrow();
        try {
            String url = session.openSession(manager.getConnectionId());
            WebSocketPort webSocket = webSocketRegistry.findUserStreamByExchangeName(exchangeName)
                    .orElseThrow(() -> new IllegalStateException("No user stream WebSocket registered for exchange: " + exchangeName));
            webSocket.connect(url, exchangeName, manager.getConnectionId());
        } catch (IOException | RuntimeException e) {
            log.error("Failed to connect user data stream - exchange={}", exchangeName, e);
            session.closeSession(manager.getConnectionId());
            manager.setConnectionResult(ConnectionResultDto.failure("connect", "Failed: " + e.getMessage()));
        }

        return Optional.of(ConnectionResultMapper.toResponse(manager.getConnectionResult(), exchangeName));
    }
}
