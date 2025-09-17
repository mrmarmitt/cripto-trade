package com.marmitt.service;

import com.marmitt.core.domain.ConnectionResult;
import com.marmitt.core.dto.websocket.ConnectionResultMapper;
import com.marmitt.core.dto.websocket.WebSocketConnectionManager;
import com.marmitt.core.dto.websocket.WebSocketConnectionResponse;
import com.marmitt.core.enums.ConnectionStatus;
import com.marmitt.core.ports.inbound.websocket.DisconnectWebSocketPort;
import com.marmitt.core.ports.outbound.ExchangeAdapterPort;
import com.marmitt.repository.InMemoryExchangeAdapterRepository;
import com.marmitt.repository.InMemoryWebSocketConnectionRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class ExchangeDisconnectService {

    private final DisconnectWebSocketPort disconnectWebSocket;
    private final InMemoryWebSocketConnectionRepository connectionRepository;
    private final InMemoryExchangeAdapterRepository adapterRepository;

    public ExchangeDisconnectService(DisconnectWebSocketPort disconnectWebSocket, InMemoryWebSocketConnectionRepository connectionRepository, InMemoryExchangeAdapterRepository adapterRepository) {
        this.disconnectWebSocket = disconnectWebSocket;
        this.connectionRepository = connectionRepository;
        this.adapterRepository = adapterRepository;
    }

    public CompletableFuture<WebSocketConnectionResponse> disconnect(String exchange) {

        try {
            ExchangeAdapterPort adapter = adapterRepository.getAdapter(exchange);
            if (adapter == null) {
                return CompletableFuture.completedFuture(
                        ConnectionResultMapper.toResponse(
                                ConnectionResult.failure("Unsupported exchange: " + exchange),
                                exchange
                        )
                );
            }

            WebSocketConnectionManager manager = connectionRepository.getConnection(exchange);
            // Adiciona contexto no MDC para logs de desconexão
            MDC.put("exchangeName", manager.getExchangeName());
            MDC.put("connectionId", manager.getConnectionId().toString());

            manager.startDisconnection();

            return disconnectWebSocket.execute(adapter.getWebSocketPort())
                    .thenApply(response -> {
                        manager.onClosed(1000, "Manual disconnect");
                        // Retorna o estado atualizado do manager, não a response original
                        return ConnectionResultMapper.toResponse(manager.getConnectionResult(), manager.getExchangeName());
                    })
                    .exceptionally(throwable -> {
                        log.error("Failed to disconnect from {} WebSocket", exchange, throwable);
                        manager.onFailure("Disconnect failed", throwable);
                        return ConnectionResultMapper.toResponse(manager.getConnectionResult(), manager.getExchangeName());
                    })
                    .whenComplete((result, throwable) -> {
                        if (throwable == null) {
                            if (result.status() == ConnectionStatus.CLOSED || result.status() == ConnectionStatus.DISCONNECTED) {
                                log.info("Successfully disconnected from {} WebSocket", exchange);
                            } else if (!result.isSuccess()) {
                                log.warn("Disconnect completed with status: {} for {}", result.status(), exchange);
                            } else {
                                log.debug("Disconnect operation completed with success status: {} for {}", result.status(), exchange);
                            }
                        }
                    });
        } finally {
            MDC.clear();
        }
    }
}
