package com.marmitt.controller;

import com.marmitt.controller.dto.WebSocketConnectRequest;
import com.marmitt.core.dto.websocket.WebSocketConnectionResponse;
import com.marmitt.core.dto.websocket.WebSocketStatsResponse;
import com.marmitt.service.ExchangeConnectService;
import com.marmitt.service.ExchangeDisconnectService;
import com.marmitt.service.ExchangeWebSocketQueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/websocket")
public class WebsocketController {

    private final ExchangeConnectService exchangeConnectionService;
    private final ExchangeDisconnectService exchangeDisconnectService;
    private final ExchangeWebSocketQueryService webSocketQueryService;

    public WebsocketController(ExchangeConnectService exchangeConnectionService,
                               ExchangeDisconnectService exchangeDisconnectService,
                               ExchangeWebSocketQueryService webSocketQueryService) {

        this.exchangeConnectionService = exchangeConnectionService;
        this.exchangeDisconnectService = exchangeDisconnectService;
        this.webSocketQueryService = webSocketQueryService;
    }

    @PostMapping("/connect")
    public WebSocketConnectionResponse connect(
            @Valid @RequestBody WebSocketConnectRequest request) {
        return exchangeConnectionService.connect(request);
    }

    @PostMapping("/disconnect")
    public WebSocketConnectionResponse disconnect(@RequestParam String exchange) {
        return exchangeDisconnectService.disconnect(exchange);
    }

    @GetMapping()
    public WebSocketConnectionResponse getConnectionResult(@RequestParam String exchange) {
        return webSocketQueryService.getStatus(exchange);
    }

    @GetMapping("/all")
    public Map<String, WebSocketConnectionResponse> getAllConnectionResult() {
        return webSocketQueryService.getAllStatus();
    }


    @GetMapping("/stats")
    public WebSocketStatsResponse getConnectionStats(@RequestParam String exchange) {
        return webSocketQueryService.getStats(exchange);
    }


    @GetMapping("/stats/all")
    public Map<String, WebSocketStatsResponse> getAllConnectionStats() {
        return webSocketQueryService.getAllStats();
    }
}
