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
import com.marmitt.core.ports.outbound.websocket.WebSocketPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPortRegistryPort;

import java.util.Optional;

public class ConnectMarketStreamUseCase implements ConnectMarketStreamPort {

    private final WebSocketConnectionRepositoryPort connectionRepository;
    private final ExchangeAdapterRepositoryPort adapterRepository;
    private final WebSocketPortRegistryPort webSocketRegistry;

    public ConnectMarketStreamUseCase(WebSocketConnectionRepositoryPort connectionRepository,
                                      ExchangeAdapterRepositoryPort adapterRepository,
                                      WebSocketPortRegistryPort webSocketRegistry) {
        this.connectionRepository = connectionRepository;
        this.adapterRepository = adapterRepository;
        this.webSocketRegistry = webSocketRegistry;
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
        ExchangeStreamingPort streaming = streamingOptional.get();
        String url = streaming.buildConnectionUrl(parameters, exchangeName);
        WebSocketPort webSocket = webSocketRegistry.findByExchangeName(exchangeName)
                .orElseThrow(() -> new IllegalStateException("No WebSocket registered for exchange: " + exchangeName));
        webSocket.connect(url, exchangeName, manager.getConnectionId());

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
}
