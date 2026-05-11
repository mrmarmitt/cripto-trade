package com.marmitt.core.ports.outbound.exchange.streaming;

import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.ProcessorResponse;
import com.marmitt.core.dto.websocket.request.MessageRequest;
import com.marmitt.core.dto.websocket.request.StreamSubscriptionRequest;

import java.util.UUID;

public interface ExchangeStreamingPort {

    String getExchangeName();

    boolean requiresPostConnection();

    void connect(StreamSubscriptionRequest parameters, String exchangeName, UUID connectionId);

    void disconnect(String exchangeName, UUID connectionId);

    void sendMessage(MessageRequest request);

    ProcessingResult<? extends ProcessorResponse> processMessage(String rawMessage, MessageContext context);
}
