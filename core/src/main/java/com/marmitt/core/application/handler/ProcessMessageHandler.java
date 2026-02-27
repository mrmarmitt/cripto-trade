package com.marmitt.core.application.handler;

import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.MarketDataDto;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.data.ProcessorResponse;
import com.marmitt.core.dto.wrapper.WebSocketConnectionManager;
import com.marmitt.core.ports.inbound.handler.HandlerProcessMessagePort;
import com.marmitt.core.ports.outbound.exchange.adapter.ExchangeAdapterPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ReceivedMessageProcessorPort;
import com.marmitt.core.ports.outbound.listener.OrderUpdateListener;
import com.marmitt.core.ports.outbound.listener.PriceUpdateListener;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.ListenerRepositoryPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * UseCase para processamento de mensagens recebidas de exchanges.
 * Coordena o processamento e notificacao de listeners usando o processor apropriado para cada exchange.
 */
@Slf4j
public class ProcessMessageHandler implements HandlerProcessMessagePort {

    private final WebSocketConnectionRepositoryPort connectionRepository;
    private final ExchangeAdapterRepositoryPort exchangeAdapterRepository;
    private final ListenerRepositoryPort listenerRepository;

    public ProcessMessageHandler(WebSocketConnectionRepositoryPort connectionRepository,
                                 ExchangeAdapterRepositoryPort exchangeAdapterRepository,
                                 ListenerRepositoryPort listenerRepository) {
        this.connectionRepository = connectionRepository;
        this.exchangeAdapterRepository = exchangeAdapterRepository;
        this.listenerRepository = listenerRepository;
    }

    @Override
    public ProcessingResult<?> execute(final String rawMessage, final MessageContext context) {
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("Raw errorMessage cannot be null or empty");
        }

        if (context == null) {
            throw new IllegalArgumentException("Message context cannot be null");
        }

        // Manager pode ser null para adapters que nao usam WebSocket real (ex: Mock)
        WebSocketConnectionManager manager = connectionRepository.getConnection(context.exchangeName());
        if (manager != null) {
            manager.onMessageReceived();
        }

        try {
            Optional<ExchangeAdapterPort> adapterOptional = exchangeAdapterRepository.findByName(context.exchangeName());

            if (adapterOptional.isEmpty()) {
                return ProcessingResult.error(context.correlationId().toString(),
                        "Exchange does not exist. ExchangeName: " + context.exchangeName());
            }

            ReceivedMessageProcessorPort messageProcessor = adapterOptional.get().getReceivedMessageProcessor();

            if (messageProcessor == null) {
                return ProcessingResult.error(context.correlationId().toString(),
                        "No processor found for exchange: " + context.exchangeName());
            }

            ProcessingResult<? extends ProcessorResponse> result =
                    messageProcessor.processMessage(rawMessage, context);

            if (isMessageProcessable(result)) {
                result.getData().ifPresent(this::notifyListeners);
            } else if (manager != null) {
                manager.onMessageError(result.getErrorMessage().orElse("No errorMessage error."));
            }

            return result;

        } catch (Exception e) {
            if (manager != null) {
                manager.onMessageError(e.getMessage());
            }
            return ProcessingResult.error(context.correlationId().toString(),
                    "Error processing errorMessage: " + e.getMessage(), e);
        }
    }

    private boolean isMessageProcessable(ProcessingResult<? extends ProcessorResponse> result) {
        return (result.isSuccess() || result.isWarning()) && result.getData().isPresent();
    }

    private void notifyListeners(final ProcessorResponse response) {
        if (response instanceof MarketDataDto marketData) {
            notifyPriceUpdate(marketData);
        } else if (response instanceof OrderDataDto orderData) {
            notifyOrderUpdate(orderData);
        }
    }

    private void notifyPriceUpdate(final MarketDataDto marketData) {
        if (marketData == null) {
            throw new IllegalArgumentException("MarketData cannot be null");
        }

        var listeners = listenerRepository.getAllPriceUpdateListeners();

        for (PriceUpdateListener listener : listeners) {
            try {
                listener.onPriceUpdate(marketData);
            } catch (Exception e) {
                log.error("Error notifying PriceUpdateListener {}: {}",
                        listener.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    private void notifyOrderUpdate(final OrderDataDto orderData) {
        if (orderData == null) {
            throw new IllegalArgumentException("OrderData cannot be null");
        }

        var listeners = listenerRepository.getAllOrderUpdateListeners();

        for (OrderUpdateListener listener : listeners) {
            try {
                if (listener.shouldProcess(orderData)) {
                    listener.onOrderUpdate(orderData);
                }
            } catch (Exception e) {
                log.error("Error notifying OrderUpdateListener {} clientOrderId={} orderId={} status={}: {}",
                        listener.getClass().getSimpleName(),
                        orderData.clientOrderId(),
                        orderData.orderId(),
                        orderData.status(),
                        e.getMessage(),
                        e);
            }
        }
    }
}
