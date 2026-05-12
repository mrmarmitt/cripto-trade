package com.marmitt.core.ports.outbound.exchange.streaming;

import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.ProcessorResponse;
import com.marmitt.core.dto.websocket.request.MessageRequest;
import com.marmitt.core.dto.websocket.request.StreamSubscriptionRequest;

public interface ExchangeStreamingPort {

    String getExchangeName();

    boolean requiresPostConnection();

    String buildConnectionUrl(StreamSubscriptionRequest parameters, String exchangeName);

    String formatMessage(MessageRequest request);

    ProcessingResult<? extends ProcessorResponse> processMessage(String rawMessage, MessageContext context);
}
