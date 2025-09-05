package com.marmitt.controller;

import com.marmitt.controller.dto.WebSocketConnectRequest;
import com.marmitt.core.dto.websocket.WebSocketConnectionResponse;
import com.marmitt.service.WebSocketExampleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/websocket")
public class WebsocketController {

    private final WebSocketExampleService webSocketService;

    public WebsocketController(WebSocketExampleService webSocketService) {
        this.webSocketService = webSocketService;
    }

    @PostMapping("/connect")
    public CompletableFuture<WebSocketConnectionResponse> connect(
            @Valid @RequestBody WebSocketConnectRequest request) {
        return webSocketService.connect(request);
    }

    @PostMapping("/disconnect")
    public CompletableFuture<WebSocketConnectionResponse> disconnect(@RequestParam String exchange) {
        return webSocketService.disconnect(exchange);
    }

    @GetMapping()
    public WebSocketConnectionResponse getConnectionResult(@RequestParam String exchange) {
        return webSocketService.getStatus(exchange);
    }
}
