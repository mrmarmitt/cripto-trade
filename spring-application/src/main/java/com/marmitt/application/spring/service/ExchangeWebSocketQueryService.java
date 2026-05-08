package com.marmitt.application.spring.service;

import com.marmitt.core.dto.websocket.response.WebSocketConnectionResponse;
import com.marmitt.core.dto.websocket.response.WebSocketStatsResponse;
import com.marmitt.core.ports.inbound.websocket.ConnectStatsWebSocketPort;
import com.marmitt.core.ports.inbound.websocket.ConnectStatusWebSocketPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * Service facade que expõe operações de consulta de WebSocket para a camada de controller.
 * 
 * Este service atua como uma facade unificada que orquestra os diferentes use cases:
 * - ExchangeStatusQueryService: Para operações de status
 * - ExchangeStatsQueryService: Para operações de estatísticas
 * 
 * Mantém uma interface limpa e coesa para a camada de apresentação,
 * enquanto delega as responsabilidades específicas para os use cases apropriados.
 */
@Service
@Slf4j
public class ExchangeWebSocketQueryService {

    private final ConnectStatusWebSocketPort statusQueryUseCase;
    private final ConnectStatsWebSocketPort statsQueryUseCase;

    public ExchangeWebSocketQueryService(
            ConnectStatusWebSocketPort statusQueryUseCase,
            ConnectStatsWebSocketPort statsQueryUseCase) {
        this.statusQueryUseCase = statusQueryUseCase;
        this.statsQueryUseCase = statsQueryUseCase;
    }

    public Map<String, WebSocketConnectionResponse> getStatus(String exchange) {
        return statusQueryUseCase.getStatus(exchange);
    }

    public Map<String, WebSocketConnectionResponse> getAllStatus() {
        return statusQueryUseCase.getAllStatus();
    }

    public boolean hasExchange(String exchange) {
        return statusQueryUseCase.hasExchange(exchange);
    }

    public Set<String> getAllExchangeNames() {
        return statusQueryUseCase.getAllExchangeNames();
    }


    public Map<String, WebSocketStatsResponse> getStats(String exchange) {
        return statsQueryUseCase.getStats(exchange);
    }

    public Map<String, WebSocketStatsResponse> getAllStats() {
        return statsQueryUseCase.getAllStats();
    }


}