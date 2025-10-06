package com.marmitt.application.spring.controller;

import com.marmitt.application.spring.controller.dto.market.MarketDataStreamRequest;
import com.marmitt.application.spring.controller.dto.market.MarketDataStreamResponse;
import com.marmitt.application.spring.controller.dto.order.OrderCancelRequest;
import com.marmitt.application.spring.controller.dto.order.OrderCreateRequest;
import com.marmitt.application.spring.controller.dto.order.OrderManagementResponse;
import com.marmitt.application.spring.controller.dto.order.OrderNotificationStreamRequest;
import com.marmitt.application.spring.controller.dto.order.OrderNotificationStreamResponse;
import com.marmitt.application.spring.service.MarketDataService;
import com.marmitt.application.spring.service.OrderManagementService;
import com.marmitt.application.spring.service.OrderNotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/websocket/exchange")
public class WebsocketExchangeController {

    private final MarketDataService marketDataService;
    private final OrderNotificationService orderNotificationService;
    private final OrderManagementService orderManagementService;

    public WebsocketExchangeController(MarketDataService marketDataService, OrderNotificationService orderNotificationService, OrderManagementService orderManagementService) {
        this.marketDataService = marketDataService;
        this.orderNotificationService = orderNotificationService;
        this.orderManagementService = orderManagementService;
    }

    @PostMapping("/market-data/subscribe")
    public ResponseEntity<MarketDataStreamResponse> subscribeMarketData(@Valid @RequestBody MarketDataStreamRequest request) {
        MarketDataStreamResponse response = marketDataService.subscribe(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/market-data/unsubscribe")
    public ResponseEntity<MarketDataStreamResponse> unsubscribeMarketData(@Valid @RequestBody MarketDataStreamRequest request) {
        MarketDataStreamResponse response = marketDataService.unsubscribe(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/order-notifications/subscribe")
    public ResponseEntity<OrderNotificationStreamResponse> subscribeOrderNotifications(@Valid @RequestBody OrderNotificationStreamRequest request) {
        OrderNotificationStreamResponse response = orderNotificationService.subscribe(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/order-notifications/unsubscribe")
    public ResponseEntity<OrderNotificationStreamResponse> unsubscribeOrderNotifications(@Valid @RequestBody OrderNotificationStreamRequest request) {
        OrderNotificationStreamResponse response = orderNotificationService.unsubscribe(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/orders/create")
    public ResponseEntity<OrderManagementResponse> createOrder(@Valid @RequestBody OrderCreateRequest request) {
        OrderManagementResponse response = orderManagementService.subscribe(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/orders/cancel")
    public ResponseEntity<OrderManagementResponse> cancelOrder(@Valid @RequestBody OrderCancelRequest request) {
        OrderManagementResponse response = orderManagementService.unsubscribe(request);
        return ResponseEntity.ok(response);
    }
}
