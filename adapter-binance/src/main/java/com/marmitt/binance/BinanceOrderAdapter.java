package com.marmitt.binance;

import com.marmitt.binance.filters.OrderFilterViolationException;
import com.marmitt.binance.filters.SymbolFilterLoadException;
import com.marmitt.core.dto.exchange.OrderSubmissionResult;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.ports.outbound.exchange.ExchangeOrderPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderExecutionPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeStreamingPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPort;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BinanceOrderAdapter implements ExchangeOrderPort {

    private final WebSocketPort userStreamWebSocket;
    private final WebSocketPort marketStreamWebSocket;
    private final ExchangeStreamingPort streamingAdapter;
    private final ExchangeOrderExecutionPort restAdapter;

    public BinanceOrderAdapter(WebSocketPort userStreamWebSocket,
                               WebSocketPort marketStreamWebSocket,
                               ExchangeStreamingPort streamingAdapter,
                               ExchangeOrderExecutionPort restAdapter) {
        this.userStreamWebSocket = userStreamWebSocket;
        this.marketStreamWebSocket = marketStreamWebSocket;
        this.streamingAdapter = streamingAdapter;
        this.restAdapter = restAdapter;
    }

    @Override
    public String getExchangeName() {
        return "BINANCE";
    }

    @Override
    public OrderSubmissionResult submitOrder(SendOrderRequest request) {
        try {
            if (userStreamWebSocket.isConnected()) {
                OrderSubmissionResult wsResult = tryViaWebSocketApi(request);
                if (wsResult != null) {
                    return wsResult;
                }
            }
            return tryViaRestThenStreaming(request);
        } catch (OrderFilterViolationException | SymbolFilterLoadException ex) {
            log.warn("submitOrder: order rejected by local filter — clientOrderId={} reason={}",
                    request.getClientOrderId(), ex.getMessage());
            return OrderSubmissionResult.failed(ex.getMessage());
        }
    }

    private OrderSubmissionResult tryViaWebSocketApi(SendOrderRequest request) {
        try {
            String message = streamingAdapter.formatMessage(request);
            userStreamWebSocket.sendMessage(message);
            log.debug("submitOrder: sent via WebSocket API — clientOrderId={}", request.getClientOrderId());
            return OrderSubmissionResult.dispatched();
        } catch (Exception ex) {
            log.warn("submitOrder: WS API send failed — falling back to REST — clientOrderId={} reason={}",
                    request.getClientOrderId(), ex.getMessage());
            return null;
        }
    }

    private OrderSubmissionResult tryViaRestThenStreaming(SendOrderRequest request) {
        try {
            OrderDataDto response = restAdapter.submitOrder(request);
            if (response == null || response.status() == null) {
                log.warn("submitOrder: REST returned empty status — falling back to streaming — clientOrderId={}",
                        request.getClientOrderId());
                return tryViaStreaming(request);
            }
            OrderDataDto normalized = normalizeClientOrderId(response, request.getClientOrderId());
            log.debug("submitOrder: REST submit reconciled — clientOrderId={} status={}",
                    normalized.clientOrderId(), normalized.status());
            return OrderSubmissionResult.completed(normalized);
        } catch (UnsupportedOperationException ex) {
            log.trace("submitOrder: REST unsupported — falling back to streaming — clientOrderId={}",
                    request.getClientOrderId());
            return tryViaStreaming(request);
        }
    }

    private OrderSubmissionResult tryViaStreaming(SendOrderRequest request) {
        String message = streamingAdapter.formatMessage(request);
        marketStreamWebSocket.sendMessage(message);
        log.debug("submitOrder: sent via market stream — clientOrderId={}", request.getClientOrderId());
        return OrderSubmissionResult.dispatched();
    }

    private static OrderDataDto normalizeClientOrderId(OrderDataDto response, String fallback) {
        if (response.clientOrderId() != null && !response.clientOrderId().isBlank()) {
            return response;
        }
        return new OrderDataDto(
                response.orderId(),
                fallback,
                response.symbol(),
                response.side(),
                response.type(),
                response.quantity(),
                response.executedQuantity(),
                response.price(),
                response.executedPrice(),
                response.fee(),
                response.status(),
                response.rejectReason(),
                response.timestamp()
        );
    }
}
