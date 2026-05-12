package com.marmitt.core.application.usecase.websocket;

import com.marmitt.core.dto.connection.ConnectionKey;
import com.marmitt.core.dto.websocket.request.MessageRequest;
import com.marmitt.core.dto.websocket.response.SendWebSocketResponse;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.ports.inbound.websocket.SendMessageWebSocketPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeStreamingPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPortRegistryPort;

import java.util.Optional;

public class SendMessageWebSocketUseCase implements SendMessageWebSocketPort {

    private final WebSocketConnectionRepositoryPort connectionRepository;
    private final ExchangeAdapterRepositoryPort adapterRepository;
    private final WebSocketPortRegistryPort webSocketRegistry;

    public SendMessageWebSocketUseCase(WebSocketConnectionRepositoryPort connectionRepository,
                                       ExchangeAdapterRepositoryPort adapterRepository,
                                       WebSocketPortRegistryPort webSocketRegistry) {
        this.connectionRepository = connectionRepository;
        this.adapterRepository = adapterRepository;
        this.webSocketRegistry = webSocketRegistry;
    }

    @Override
    public SendWebSocketResponse execute(MessageRequest request) {
        WebSocketConnectionManager manager = connectionRepository.getConnection(ConnectionKey.market(request.getExchangeName()));

        Optional<ExchangeStreamingPort> streamingOptional = adapterRepository.findStreamingByName(request.getExchangeName());
        if (streamingOptional.isEmpty()) {
            return SendWebSocketResponse.failure(request.getExchangeName(), "Exchange does not exist");
        }

        manager.addRequestToHistory(request);
        String message = streamingOptional.get().formatMessage(request);
        WebSocketPort webSocket = webSocketRegistry.findByExchangeName(request.getExchangeName())
                .orElseThrow(() -> new IllegalStateException("No WebSocket registered for exchange: " + request.getExchangeName()));
        webSocket.sendMessage(message);

        return SendWebSocketResponse.success(request.getExchangeName());
    }
}
