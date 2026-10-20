package com.marmitt.application.spring.controller;

import com.marmitt.application.spring.service.MarketDataService;
import com.marmitt.application.spring.service.OrderNotificationService;
import com.marmitt.core.dto.websocket.request.MarketDataStreamRequest;
import com.marmitt.core.dto.websocket.request.OrderNotificationStreamRequest;
import com.marmitt.core.dto.websocket.response.MarketDataStreamResponse;
import com.marmitt.core.dto.websocket.response.OrderNotificationStreamResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Assinatura manual de streams (market data, notificacoes de ordem). Hoje e o unico jeito de comecar a
 * operar apos o boot. Ordens nao passam por aqui: toda ordem nasce de uma transacao do core (T55).
 */
@RestController
@RequestMapping("/api/websocket/exchange")
public class WebsocketExchangeController {

    private final MarketDataService marketDataService;
    private final OrderNotificationService orderNotificationService;

    public WebsocketExchangeController(MarketDataService marketDataService, OrderNotificationService orderNotificationService) {
        this.marketDataService = marketDataService;
        this.orderNotificationService = orderNotificationService;
    }

    @PostMapping("/market-data/subscribe")
    public ResponseEntity<MarketDataStreamResponse> subscribeMarketData(@RequestBody MarketDataStreamRequest request) {
        MarketDataStreamResponse response = marketDataService.subscribe(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/market-data/unsubscribe")
    public ResponseEntity<MarketDataStreamResponse> unsubscribeMarketData(@RequestBody MarketDataStreamRequest request) {
        MarketDataStreamResponse response = marketDataService.unsubscribe(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/order-notifications/subscribe")
    public ResponseEntity<OrderNotificationStreamResponse> subscribeOrderNotifications(@RequestBody OrderNotificationStreamRequest request) {
        OrderNotificationStreamResponse response = orderNotificationService.subscribe(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/order-notifications/unsubscribe")
    public ResponseEntity<OrderNotificationStreamResponse> unsubscribeOrderNotifications(@RequestBody OrderNotificationStreamRequest request) {
        OrderNotificationStreamResponse response = orderNotificationService.unsubscribe(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
