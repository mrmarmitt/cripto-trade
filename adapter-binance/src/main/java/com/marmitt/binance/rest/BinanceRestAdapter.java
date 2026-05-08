package com.marmitt.binance.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.binance.auth.BinanceRequestSigner;
import com.marmitt.core.dto.websocket.data.AccountDataDto;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.request.SendCancelOrderRequest;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.ports.outbound.http.HttpClientPort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BinanceRestAdapter {

    private static final String ORDER_PATH       = "/api/v3/order";
    private static final String OPEN_ORDERS_PATH = "/api/v3/openOrders";
    private static final String ACCOUNT_PATH     = "/api/v3/account";

    private final BinanceRestClient client;
    private final BinanceOrderMapper orderMapper;
    private final BinanceAccountMapper accountMapper;

    public BinanceRestAdapter(String restBaseUrl, BinanceRequestSigner signer,
                              HttpClientPort httpClient, ObjectMapper objectMapper) {
        this.client        = new BinanceRestClient(restBaseUrl, signer, httpClient);
        this.orderMapper   = new BinanceOrderMapper(objectMapper);
        this.accountMapper = new BinanceAccountMapper(objectMapper);
    }

    public OrderDataDto submitOrder(SendOrderRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("symbol", request.getSymbol());
        params.put("side", request.getOrderSide().name());
        params.put("type", toRestOrderType(request));
        params.put("quantity", request.getQuantity().toPlainString());
        params.put("newClientOrderId", request.getClientOrderId());
        if (request.getPrice() != null && request.getPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
            params.put("price", request.getPrice().toPlainString());
            params.put("timeInForce", "GTC");
        }
        String json = client.postForm(ORDER_PATH, params);
        return orderMapper.fromJson(json);
    }

    public OrderDataDto cancelOrder(SendCancelOrderRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("symbol", request.getSymbol());
        params.put("origClientOrderId", request.getClientOrderId());
        String json = client.deleteQuery(ORDER_PATH, params);
        return orderMapper.fromJson(json);
    }

    public Optional<OrderDataDto> queryOrderByClientOrderId(String symbol, String clientOrderId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("symbol", symbol);
        params.put("origClientOrderId", clientOrderId);
        String json = client.getJsonOrNull(ORDER_PATH, params);
        return json == null ? Optional.empty() : Optional.of(orderMapper.fromJson(json));
    }

    public Optional<OrderDataDto> queryOrderByExchangeOrderId(String symbol, String exchangeOrderId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("symbol", symbol);
        params.put("orderId", exchangeOrderId);
        String json = client.getJsonOrNull(ORDER_PATH, params);
        return json == null ? Optional.empty() : Optional.of(orderMapper.fromJson(json));
    }

    public List<OrderDataDto> listOpenOrdersBySymbol(String symbol) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("symbol", symbol);
        String json = client.getJson(OPEN_ORDERS_PATH, params);
        return orderMapper.listFromJson(json);
    }

    public List<OrderDataDto> listAllOpenOrders() {
        String json = client.getJson(OPEN_ORDERS_PATH, Map.of());
        return orderMapper.listFromJson(json);
    }

    public AccountDataDto queryAccountSnapshot() {
        String json = client.getJson(ACCOUNT_PATH, Map.of());
        return accountMapper.fromJson(json);
    }

    private String toRestOrderType(SendOrderRequest request) {
        return switch (request.getOrderType()) {
            case MARKET           -> "MARKET";
            case LIMIT            -> "LIMIT";
            case STOP_LOSS        -> "STOP_LOSS";
            case STOP_LOSS_LIMIT  -> "STOP_LOSS_LIMIT";
            case TAKE_PROFIT      -> "TAKE_PROFIT";
            case TAKE_PROFIT_LIMIT -> "TAKE_PROFIT_LIMIT";
        };
    }
}
