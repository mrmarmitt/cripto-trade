package com.marmitt.core.application.usecase.websocket;

import com.marmitt.core.dto.connection.ConnectionKey;
import com.marmitt.core.dto.connection.ConnectionResultDto;
import com.marmitt.core.dto.websocket.mapper.ConnectionResultMapper;
import com.marmitt.core.dto.websocket.request.StreamSubscriptionRequest;
import com.marmitt.core.dto.websocket.response.WebSocketConnectionResponse;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.enums.ConnectionStatus;
import com.marmitt.core.ports.inbound.websocket.ConnectWebSocketPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeStreamingPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeUserStreamPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

public class ConnectWebSocketUseCase implements ConnectWebSocketPort {

    private static final Logger log = LoggerFactory.getLogger(ConnectWebSocketUseCase.class);

    private final WebSocketConnectionRepositoryPort connectionRepository;
    private final ExchangeAdapterRepositoryPort adapterRepository;

    public ConnectWebSocketUseCase(WebSocketConnectionRepositoryPort connectionRepository,
                                   ExchangeAdapterRepositoryPort adapterRepository) {
        this.connectionRepository = connectionRepository;
        this.adapterRepository = adapterRepository;
    }

    @Override
    public WebSocketConnectionResponse execute(final String exchangeName, final StreamSubscriptionRequest parameters) {
        ConnectionKey marketKey = ConnectionKey.market(exchangeName);
        connectionRepository.registerConnection(marketKey);
        WebSocketConnectionManager manager = connectionRepository.getConnection(marketKey);

        Optional<ExchangeStreamingPort> streamingOptional = adapterRepository.findStreamingByName(exchangeName);
        if (streamingOptional.isEmpty()) {
            return ConnectionResultMapper.toResponse(
                    ConnectionResultDto.failure(this.getClass().getSimpleName(), "Exchange does not exist"),
                    exchangeName);
        }

        ConnectionStatus currentStatus = manager.getConnectionResult().status();
        boolean isReconnect = currentStatus == ConnectionStatus.ERROR || currentStatus == ConnectionStatus.CLOSED;
        manager.setConnectionResult(isReconnect
                ? ConnectionResultDto.reconnecting(1, 1)
                : ConnectionResultDto.connecting());
        manager.addRequestToHistory(parameters);

        ExchangeStreamingPort streaming = streamingOptional.get();
        String connectionUrl = streaming.getUrlBuilder().buildConnectionUrl(parameters);
        streaming.getWebSocketPort().connect(connectionUrl, exchangeName, manager.getConnectionId());

        adapterRepository.findUserStreamByName(exchangeName).ifPresent(userStream ->
                connectUserStream(userStream, exchangeName));

        return ConnectionResultMapper.toResponse(manager.getConnectionResult(), exchangeName);
    }

    private void connectUserStream(ExchangeUserStreamPort userStream, String exchangeName) {
        ConnectionKey userKey = ConnectionKey.userStream(exchangeName);
        connectionRepository.registerConnection(userKey);
        WebSocketConnectionManager userManager = connectionRepository.getConnection(userKey);
        userManager.setConnectionResult(ConnectionResultDto.connecting());
        try {
            userStream.connect(userManager.getConnectionId());
        } catch (IOException e) {
            log.error("Failed to connect user data stream - exchange={}", exchangeName, e);
            userManager.setConnectionResult(ConnectionResultDto.failure("connect", "Failed: " + e.getMessage()));
        }
    }
}
