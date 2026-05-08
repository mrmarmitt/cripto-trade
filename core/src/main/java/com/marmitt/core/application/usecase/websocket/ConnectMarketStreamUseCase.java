package com.marmitt.core.application.usecase.websocket;

import com.marmitt.core.dto.connection.ConnectionKey;
import com.marmitt.core.dto.connection.ConnectionResultDto;
import com.marmitt.core.dto.websocket.mapper.ConnectionResultMapper;
import com.marmitt.core.dto.websocket.request.StreamSubscriptionRequest;
import com.marmitt.core.dto.websocket.response.WebSocketConnectionResponse;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.enums.ConnectionStatus;
import com.marmitt.core.ports.inbound.websocket.ConnectMarketStreamPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeStreamingPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;

import java.util.Optional;

public class ConnectMarketStreamUseCase implements ConnectMarketStreamPort {

    private final WebSocketConnectionRepositoryPort connectionRepository;
    private final ExchangeAdapterRepositoryPort adapterRepository;

    public ConnectMarketStreamUseCase(WebSocketConnectionRepositoryPort connectionRepository,
                                      ExchangeAdapterRepositoryPort adapterRepository) {
        this.connectionRepository = connectionRepository;
        this.adapterRepository = adapterRepository;
    }

    @Override
    public WebSocketConnectionResponse execute(final String exchangeName, final StreamSubscriptionRequest parameters) {
        Optional<ExchangeStreamingPort> streamingOptional = adapterRepository.findStreamingByName(exchangeName);
        if (streamingOptional.isEmpty()) {
            return ConnectionResultMapper.toResponse(
                    ConnectionResultDto.failure(this.getClass().getSimpleName(), "Exchange does not exist"),
                    exchangeName);
        }

        WebSocketConnectionManager manager = prepareMarketManager(exchangeName, parameters);
        connectMarketStream(streamingOptional.get(), exchangeName, manager, parameters);

        return ConnectionResultMapper.toResponse(manager.getConnectionResult(), exchangeName);
    }

    private WebSocketConnectionManager prepareMarketManager(String exchangeName, StreamSubscriptionRequest parameters) {
        ConnectionKey marketKey = ConnectionKey.market(exchangeName);
        connectionRepository.registerConnection(marketKey);
        WebSocketConnectionManager manager = connectionRepository.getConnection(marketKey);

        ConnectionStatus currentStatus = manager.getConnectionResult().status();
        boolean isReconnect = currentStatus == ConnectionStatus.ERROR || currentStatus == ConnectionStatus.CLOSED;
        manager.setConnectionResult(isReconnect
                ? ConnectionResultDto.reconnecting(1, 1)
                : ConnectionResultDto.connecting());
        manager.addRequestToHistory(parameters);

        return manager;
    }

    private void connectMarketStream(ExchangeStreamingPort streaming, String exchangeName,
                                     WebSocketConnectionManager manager, StreamSubscriptionRequest parameters) {
        String connectionUrl = streaming.getUrlBuilder().buildConnectionUrl(parameters);
        streaming.getWebSocketPort().connect(connectionUrl, exchangeName, manager.getConnectionId());
    }
}
