package com.marmitt.core.ports.outbound.exchange.streaming;

import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.ProcessorResponse;

import java.io.IOException;
import java.util.UUID;

public interface ExchangeUserStreamPort {

    String getExchangeName();

    /**
     * Prepares the connection: obtains credentials/session tokens, starts keep-alive,
     * and returns the WebSocket URL for core to connect.
     */
    String prepareConnection(UUID connectionId) throws IOException;

    /**
     * Releases exchange-side resources: revokes session tokens, stops keep-alive.
     * Core disconnects the WebSocket separately.
     */
    void onDisconnect(UUID connectionId);

    ProcessingResult<? extends ProcessorResponse> processMessage(String rawMessage, MessageContext context);
}
