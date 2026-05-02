package com.marmitt.core.application.usecase.websocket;

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

    private void connectUserStream(ExchangeUserStreamPort userStream, java.util.UUID connectionId) {
        try {
            userStream.connect(connectionId);
        } catch (IOException e) {
            log.error("Failed to connect user data stream for exchange={}", userStream.getExchangeName(), e);
        }
    }

    @Override
    public WebSocketConnectionResponse execute(final String exchangeName, final StreamSubscriptionRequest parameters) {
        connectionRepository.registerConnection(exchangeName);
        WebSocketConnectionManager manager = connectionRepository.getConnection(exchangeName);

        Optional<ExchangeStreamingPort> streamingOptional = adapterRepository.findStreamingByName(exchangeName);
        if (streamingOptional.isEmpty()) {
            return ConnectionResultMapper.toResponse(
                    ConnectionResultDto.failure(this.getClass().getSimpleName(), "Exchange does not exist"),
                    exchangeName);
        }

        ConnectionStatus currentStatus = manager.getConnectionResult().status();
        boolean isReconnect = currentStatus == ConnectionStatus.ERROR
                           || currentStatus == ConnectionStatus.CLOSED;

        if (isReconnect) {
            manager.setConnectionResult(ConnectionResultDto.reconnecting(1, 1));
        } else {
            manager.setConnectionResult(ConnectionResultDto.connecting());
        }

        manager.addRequestToHistory(parameters);

        ExchangeStreamingPort streaming = streamingOptional.get();
        String connectionUrl = streaming.getUrlBuilder().buildConnectionUrl(parameters);
        streaming.getWebSocketPort().connect(connectionUrl, exchangeName, manager.getConnectionId());

        adapterRepository.findUserStreamByName(exchangeName).ifPresent(userStream ->
                connectUserStream(userStream, manager.getConnectionId()));

        return ConnectionResultMapper.toResponse(manager.getConnectionResult(), exchangeName);
    }
}

