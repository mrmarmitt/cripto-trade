package com.marmitt.application.spring.infrastructure.exchange;

import com.marmitt.core.dto.runner.OrderDispatchCommand;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.enums.OrderSide;
import com.marmitt.core.enums.OrderType;
import com.marmitt.core.enums.TransactionType;
import com.marmitt.core.ports.inbound.runner.OrderConciliationPort;
import com.marmitt.core.ports.outbound.exchange.OrderDispatchPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderExecutionPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeStreamingPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPortRegistryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderDispatchAdapter implements OrderDispatchPort {

    private final ExchangeAdapterRepositoryPort exchangeAdapterRepository;
    private final OrderConciliationPort orderConciliation;
    private final WebSocketPortRegistryPort webSocketRegistry;

    public OrderDispatchAdapter(ExchangeAdapterRepositoryPort exchangeAdapterRepository,
                                OrderConciliationPort orderConciliation,
                                WebSocketPortRegistryPort webSocketRegistry) {
        this.exchangeAdapterRepository = exchangeAdapterRepository;
        this.orderConciliation = orderConciliation;
        this.webSocketRegistry = webSocketRegistry;
    }

    @Override
    public void dispatch(OrderDispatchCommand command) {
        SendOrderRequest request = toSendOrderRequest(command, command.exchangeId());

        if (!tryDispatchViaRest(request, command.exchangeId())) {
            dispatchViaStreaming(request, command.exchangeId());
        }

        log.debug("dispatch: order sent - clientOrderId={} exchange={} symbol={} type={}",
                command.clientOrderId(), command.exchangeId(), command.symbol(), command.type());
    }

    private boolean tryDispatchViaRest(SendOrderRequest request, String exchangeId) {
        ExchangeOrderExecutionPort executionPort = exchangeAdapterRepository
                .findOrderExecutionByName(exchangeId)
                .orElse(null);
        if (executionPort == null) {
            return false;
        }

        try {
            OrderDataDto response = executionPort.submitOrder(request);
            if (response == null || response.status() == null) {
                log.warn("dispatch: REST submit returned empty status - fallback to streaming clientOrderId={} exchange={}",
                        request.getClientOrderId(), exchangeId);
                return false;
            }

            OrderDataDto normalized = normalizeClientOrderId(response, request.getClientOrderId());
            orderConciliation.execute(normalized);
            log.debug("dispatch: REST submit reconciled - clientOrderId={} exchange={} status={}",
                    normalized.clientOrderId(), exchangeId, normalized.status());
            return true;
        } catch (UnsupportedOperationException ex) {
            log.trace("dispatch: REST submit unsupported for exchange={} - fallback to streaming", exchangeId);
            return false;
        }
    }

    private void dispatchViaStreaming(SendOrderRequest request, String exchangeId) {
        ExchangeStreamingPort streamingPort = exchangeAdapterRepository
                .findStreamingByName(exchangeId)
                .orElseThrow(() -> new IllegalStateException(
                        "No streaming capability found for exchangeId: " + exchangeId));

        String message = streamingPort.formatMessage(request);
        WebSocketPort webSocket = webSocketRegistry.findByExchangeName(exchangeId)
                .orElseThrow(() -> new IllegalStateException(
                        "No WebSocket registered for exchangeId: " + exchangeId));
        webSocket.sendMessage(message);
    }

    private SendOrderRequest toSendOrderRequest(OrderDispatchCommand command, String exchangeName) {
        OrderSide side = command.type() == TransactionType.BUY ? OrderSide.BUY : OrderSide.SELL;
        return new SendOrderRequest(
                exchangeName,
                command.symbol(),
                command.quantity(),
                command.price(),
                OrderType.LIMIT,
                side,
                command.clientOrderId()
        );
    }

    private static OrderDataDto normalizeClientOrderId(OrderDataDto response, String fallbackClientOrderId) {
        if (response.clientOrderId() != null && !response.clientOrderId().isBlank()) {
            return response;
        }
        return new OrderDataDto(
                response.orderId(),
                fallbackClientOrderId,
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
