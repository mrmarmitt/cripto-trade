package com.marmitt.binance.processor.receive;

import com.fasterxml.jackson.databind.JsonNode;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.ProcessorResponse;

interface BinanceEventProcessor<T extends ProcessorResponse> {

    /**
     * The value of the Binance "e" (event type) field this processor handles.
     * Return {@code null} for events that have no "e" field (e.g. bookTicker).
     */
    String eventType();

    ProcessingResult<? extends ProcessorResponse> process(JsonNode data, MessageContext context);
}
