package com.marmitt.core.ports.outbound.exchange.streaming;

import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.ProcessorResponse;

import java.io.IOException;
import java.util.UUID;

public interface ExchangeUserStreamPort {

    String getExchangeName();

    void connect(UUID connectionId) throws IOException;

    void disconnect(UUID connectionId);

    ProcessingResult<? extends ProcessorResponse> processMessage(String rawMessage, MessageContext context);
}
