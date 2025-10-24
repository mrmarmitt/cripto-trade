package com.marmitt.core.application.usecase.websocket;

import com.marmitt.core.domain.ConnectionResult;
import com.marmitt.core.dto.websocket.mapper.ConnectionResultMapper;
import com.marmitt.core.dto.websocket.request.StreamSubscriptionRequest;
import com.marmitt.core.dto.websocket.response.WebSocketConnectionResponse;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.ports.inbound.websocket.ConnectWebSocketPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ExchangeAdapterPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;

public class ConnectWebSocketUseCase implements ConnectWebSocketPort {

    private final WebSocketConnectionRepositoryPort connectionRepository;
    private final ExchangeAdapterRepositoryPort adapterRepository;

    public ConnectWebSocketUseCase(WebSocketConnectionRepositoryPort connectionRepository,
                                   ExchangeAdapterRepositoryPort adapterRepository) {
        this.connectionRepository = connectionRepository;
        this.adapterRepository = adapterRepository;
    }

    @Override
    public WebSocketConnectionResponse execute(final String exchangeName, final StreamSubscriptionRequest parameters) {

        connectionRepository.registerConnection(exchangeName);
        WebSocketConnectionManager manager = connectionRepository.getConnection(exchangeName);
        ExchangeAdapterPort adapter = adapterRepository.getAdapter(exchangeName);

        manager.resetConnection();
        manager.setConnectionResult(ConnectionResult.connecting());
        manager.addRequestToHistory(parameters);

        String connectionUrl = adapter.getUrlBuilder().buildConnectionUrl(parameters);
        adapter.getWebSocketPort().connect(connectionUrl, exchangeName, manager.getConnectionId());

        return ConnectionResultMapper.toResponse(manager.getConnectionResult(), exchangeName);
    }

}