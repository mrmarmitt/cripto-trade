package com.marmitt.application.spring.infrastructure.exchange;

import com.marmitt.core.dto.runner.OrderDispatchCommand;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.enums.OrderSide;
import com.marmitt.core.enums.OrderType;
import com.marmitt.core.enums.TransactionType;
import com.marmitt.core.ports.outbound.exchange.OrderDispatchPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderExecutionPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeStreamingPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderDispatchAdapter implements OrderDispatchPort {

    private final ExchangeAdapterRepositoryPort exchangeAdapterRepository;

    public OrderDispatchAdapter(ExchangeAdapterRepositoryPort exchangeAdapterRepository) {
        this.exchangeAdapterRepository = exchangeAdapterRepository;
    }

    @Override
    public void dispatch(OrderDispatchCommand command) {
        SendOrderRequest request = toSendOrderRequest(command, command.exchangeId());

        if (!tryDispatchViaRest(request, command.exchangeId(), command.clientOrderId())) {
            dispatchViaStreaming(request, command.exchangeId());
        }

        log.debug("dispatch: order sent - clientOrderId={} exchange={} symbol={} type={}",
                command.clientOrderId(), command.exchangeId(), command.symbol(), command.type());
    }

    private boolean tryDispatchViaRest(SendOrderRequest request, String exchangeId, String clientOrderId) {
        ExchangeOrderExecutionPort executionPort = exchangeAdapterRepository
                .findOrderExecutionByName(exchangeId)
                .orElse(null);
        if (executionPort == null) {
            return false;
        }

        try {
            executionPort.submitOrder(request);
            log.debug("dispatch: REST submit accepted - clientOrderId={} exchange={}", clientOrderId, exchangeId);
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

        String json = streamingPort.getSenderMessageProcessor().execute(request);
        streamingPort.getWebSocketPort().sendMessage(json);
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
}

