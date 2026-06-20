package com.marmitt.application.spring.controller;

import com.marmitt.application.spring.service.ExchangeConnectService;
import com.marmitt.application.spring.service.ExchangeDisconnectService;
import com.marmitt.application.spring.service.ExchangeWebSocketQueryService;
import com.marmitt.core.dto.websocket.request.WebSocketConnectRequest;
import com.marmitt.core.dto.websocket.response.WebSocketConnectionResponse;
import com.marmitt.core.dto.websocket.response.WebSocketStatsResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/websocket")
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
    public ResponseEntity<Map<String, WebSocketConnectionResponse>> connect(
            @RequestBody WebSocketConnectRequest request) {
        Map<String, WebSocketConnectionResponse> response = exchangeConnectionService.connect(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/disconnect")
    public ResponseEntity<WebSocketConnectionResponse> disconnect(@RequestParam String exchange) {
        WebSocketConnectionResponse response = exchangeDisconnectService.disconnect(exchange);
        return ResponseEntity.ok(response);
    }

    @GetMapping()
    public ResponseEntity<Map<String, WebSocketConnectionResponse>> getConnectionResult(@RequestParam String exchange) {
        Map<String, WebSocketConnectionResponse> response = webSocketQueryService.getStatus(exchange);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/all")
    public ResponseEntity<Map<String, WebSocketConnectionResponse>> getAllConnectionResult() {
        Map<String, WebSocketConnectionResponse> response = webSocketQueryService.getAllStatus();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, WebSocketStatsResponse>> getConnectionStats(@RequestParam String exchange) {
        Map<String, WebSocketStatsResponse> response = webSocketQueryService.getStats(exchange);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats/all")
    public ResponseEntity<Map<String, WebSocketStatsResponse>> getAllConnectionStats() {
        Map<String, WebSocketStatsResponse> response = webSocketQueryService.getAllStats();
        return ResponseEntity.ok(response);
    }
}
