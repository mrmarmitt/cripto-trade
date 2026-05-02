package com.marmitt.core.application.handler;

import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.data.ProcessorResponse;
import com.marmitt.core.ports.inbound.handler.HandlerProcessUserMessagePort;
import com.marmitt.core.ports.outbound.exchange.adapter.ReceivedMessageProcessorPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeUserStreamPort;
import com.marmitt.core.ports.outbound.listener.OrderUpdateListener;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.ListenerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProcessUserMessageHandler implements HandlerProcessUserMessagePort {

    private final ExchangeAdapterRepositoryPort exchangeAdapterRepository;
    private final ListenerRepositoryPort listenerRepository;

    public ProcessUserMessageHandler(ExchangeAdapterRepositoryPort exchangeAdapterRepository,
                                     ListenerRepositoryPort listenerRepository) {
        this.exchangeAdapterRepository = exchangeAdapterRepository;
        this.listenerRepository = listenerRepository;
    }

    @Override
    public ProcessingResult<?> execute(String rawMessage, MessageContext context) {
        if (rawMessage == null || rawMessage.isBlank()) {
            throw new IllegalArgumentException("Raw message cannot be null or empty");
        }

        String correlationId = context.correlationId().toString();

        try {
            ExchangeUserStreamPort userStream = exchangeAdapterRepository
                    .findUserStreamByName(context.exchangeName())
                    .orElse(null);

            if (userStream == null) {
                return ProcessingResult.error(correlationId,
                        "No user stream adapter found for exchange: " + context.exchangeName());
            }

            ReceivedMessageProcessorPort processor = userStream.getReceivedMessageProcessor();
            ProcessingResult<? extends ProcessorResponse> result = processor.processMessage(rawMessage, context);

            if (isProcessable(result)) {
                result.getData().ifPresent(this::notifyOrderUpdate);
            } else {
                log.warn("User data message not processable: correlationId={} error={}",
                        correlationId, result.getErrorMessage().orElse("unknown"));
            }

            return result;

        } catch (Exception e) {
            log.error("Error processing user data message: correlationId={}", correlationId, e);
            return ProcessingResult.error(correlationId, "Error processing user data message: " + e.getMessage(), e);
        }
    }

    private boolean isProcessable(ProcessingResult<? extends ProcessorResponse> result) {
        return (result.isSuccess() || result.isWarning()) && result.getData().isPresent();
    }

    private void notifyOrderUpdate(ProcessorResponse response) {
        if (!(response instanceof OrderDataDto orderData)) {
            log.warn("Unexpected response type from user stream: {}", response.getClass().getSimpleName());
            return;
        }

        for (OrderUpdateListener listener : listenerRepository.getAllOrderUpdateListeners()) {
            try {
                if (listener.shouldProcess(orderData)) {
                    listener.onOrderUpdate(orderData);
                }
            } catch (Exception e) {
                log.error("Error notifying OrderUpdateListener {} clientOrderId={} status={}: {}",
                        listener.getClass().getSimpleName(),
                        orderData.clientOrderId(),
                        orderData.status(),
                        e.getMessage(), e);
            }
        }
    }
}
