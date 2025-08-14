package com.marmitt.ctrade.controller;

import com.marmitt.ctrade.application.service.TradingService;
import com.marmitt.ctrade.controller.dto.OrderRequest;
import com.marmitt.ctrade.controller.dto.OrderResponse;
import com.marmitt.ctrade.controller.dto.PriceResponse;
import com.marmitt.ctrade.domain.entity.Order;
import com.marmitt.ctrade.domain.entity.TradingPair;
import com.marmitt.ctrade.domain.valueobject.Price;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/trading")
@RequiredArgsConstructor
public class TradingController {

    private final TradingService tradingService;

    @PostMapping("/orders/buy")
    public ResponseEntity<OrderResponse> placeBuyOrder(@Valid @RequestBody OrderRequest request) {
        TradingPair tradingPair = new TradingPair(request.getTradingPair());
        Order order = tradingService.placeBuyOrder(tradingPair, request.getQuantity(), request.getPrice());
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.fromOrder(order));
    }

    @PostMapping("/orders/sell")
    public ResponseEntity<OrderResponse> placeSellOrder(@Valid @RequestBody OrderRequest request) {
        TradingPair tradingPair = new TradingPair(request.getTradingPair());
        Order order = tradingService.placeSellOrder(tradingPair, request.getQuantity(), request.getPrice());
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.fromOrder(order));
    }

    @PostMapping("/orders/market-buy")
    public ResponseEntity<OrderResponse> placeMarketBuyOrder(@Valid @RequestBody OrderRequest request) {
        TradingPair tradingPair = new TradingPair(request.getTradingPair());
        Order order = tradingService.placeMarketBuyOrder(tradingPair, request.getQuantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.fromOrder(order));
    }

    @DeleteMapping("/orders/{orderId}")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable String orderId) {
        Order order = tradingService.cancelOrder(orderId);
        return ResponseEntity.ok(OrderResponse.fromOrder(order));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderResponse> getOrderStatus(@PathVariable String orderId) {
        Order order = tradingService.getOrderStatus(orderId);
        return ResponseEntity.ok(OrderResponse.fromOrder(order));
    }

    @GetMapping("/orders/active")
    public ResponseEntity<List<OrderResponse>> getActiveOrders() {
        List<Order> orders = tradingService.getActiveOrders();
        List<OrderResponse> responses = orders.stream()
            .map(OrderResponse::fromOrder)
            .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/price/{baseCurrency}/{quoteCurrency}")
    public ResponseEntity<PriceResponse> getCurrentPrice(@PathVariable String baseCurrency, @PathVariable String quoteCurrency) {
        TradingPair pair = new TradingPair(baseCurrency, quoteCurrency);
        Price price = tradingService.getCurrentPrice(pair);
        return ResponseEntity.ok(new PriceResponse(pair.getSymbol(), price.getValue()));
    }
}