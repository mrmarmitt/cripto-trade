package com.marmitt.coinbase.processor.receive;

import com.fasterxml.jackson.databind.JsonNode;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.ProcessorResponse;

interface CoinbaseEventProcessor<T extends ProcessorResponse> {

    String eventType();

    ProcessingResult<? extends ProcessorResponse> process(JsonNode data, MessageContext context);
}
