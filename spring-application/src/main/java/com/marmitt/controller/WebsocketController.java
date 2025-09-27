package com.marmitt.controller;

import com.marmitt.controller.dto.MarketDataSubscribeRequest;
import com.marmitt.controller.dto.MarketDataSubscribeResponse;
import com.marmitt.controller.dto.OrderCancelRequest;
import com.marmitt.controller.dto.OrderCreateRequest;
import com.marmitt.controller.dto.OrderManagementResponse;
import com.marmitt.controller.dto.OrderNotificationSubscribeRequest;
import com.marmitt.controller.dto.OrderNotificationSubscribeResponse;
import com.marmitt.controller.dto.WebSocketConnectRequest;
import com.marmitt.core.dto.websocket.response.WebSocketConnectionResponse;
import com.marmitt.core.dto.websocket.response.WebSocketStatsResponse;
import com.marmitt.service.ExchangeConnectService;
import com.marmitt.service.ExchangeDisconnectService;
import com.marmitt.service.ExchangeWebSocketQueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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

    @PostMapping("/market-data/subscribe")
    public MarketDataSubscribeResponse subscribeMarketData(@Valid @RequestBody MarketDataSubscribeRequest request) {
        // TODO: Implementar lógica no service
        return MarketDataSubscribeResponse.successfully("subscription");
    }

    @PostMapping("/market-data/unsubscribe")
    public MarketDataSubscribeResponse unsubscribeMarketData(@Valid @RequestBody MarketDataSubscribeRequest request) {
        // TODO: Implementar lógica no service
        return MarketDataSubscribeResponse.successfully("unsubscription");
    }

    @PostMapping("/order-notifications/subscribe")
    public OrderNotificationSubscribeResponse subscribeOrderNotifications(@Valid @RequestBody OrderNotificationSubscribeRequest request) {
        // TODO: Implementar lógica no service
        return OrderNotificationSubscribeResponse.successfully("subscription");
    }

    @PostMapping("/order-notifications/unsubscribe")
    public OrderNotificationSubscribeResponse unsubscribeOrderNotifications(@Valid @RequestBody OrderNotificationSubscribeRequest request) {
        // TODO: Implementar lógica no service
        return OrderNotificationSubscribeResponse.successfully("unsubscription");
    }

    @PostMapping("/orders/create")
    public OrderManagementResponse createOrder(@Valid @RequestBody OrderCreateRequest request) {
        // TODO: Implementar lógica no service
        return OrderManagementResponse.successfully("creation");
    }

    @PostMapping("/orders/cancel")
    public OrderManagementResponse cancelOrder(@Valid @RequestBody OrderCancelRequest request) {
        // TODO: Implementar lógica no service
        return OrderManagementResponse.successfully("cancellation");
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
