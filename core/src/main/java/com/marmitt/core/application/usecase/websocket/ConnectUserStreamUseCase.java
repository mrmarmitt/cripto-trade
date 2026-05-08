package com.marmitt.core.application.usecase.websocket;

import com.marmitt.core.dto.connection.ConnectionKey;
import com.marmitt.core.dto.connection.ConnectionResultDto;
import com.marmitt.core.dto.websocket.mapper.ConnectionResultMapper;
import com.marmitt.core.dto.websocket.response.WebSocketConnectionResponse;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.enums.ConnectionStatus;
import com.marmitt.core.ports.inbound.websocket.ConnectUserStreamPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeUserStreamPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

public class ConnectUserStreamUseCase implements ConnectUserStreamPort {

    private static final Logger log = LoggerFactory.getLogger(ConnectUserStreamUseCase.class);

    private final WebSocketConnectionRepositoryPort connectionRepository;
    private final ExchangeAdapterRepositoryPort adapterRepository;

    public ConnectUserStreamUseCase(WebSocketConnectionRepositoryPort connectionRepository,
                                    ExchangeAdapterRepositoryPort adapterRepository) {
        this.connectionRepository = connectionRepository;
        this.adapterRepository = adapterRepository;
    }

    @Override
    public Optional<WebSocketConnectionResponse> execute(String exchangeName) {
        Optional<ExchangeUserStreamPort> userStreamOpt = adapterRepository.findUserStreamByName(exchangeName);
        if (userStreamOpt.isEmpty()) {
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

        try {
            userStreamOpt.get().connect(manager.getConnectionId());
        } catch (IOException e) {
            log.error("Failed to connect user data stream - exchange={}", exchangeName, e);
            manager.setConnectionResult(ConnectionResultDto.failure("connect", "Failed: " + e.getMessage()));
        }

        return Optional.of(ConnectionResultMapper.toResponse(manager.getConnectionResult(), exchangeName));
    }
}
