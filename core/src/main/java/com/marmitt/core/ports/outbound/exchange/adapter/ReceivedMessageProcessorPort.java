package com.marmitt.core.ports.outbound.exchange.adapter;

import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.ProcessorResponse;

public interface ReceivedMessageProcessorPort {

    ProcessingResult<? extends ProcessorResponse> processMessage(String rawMessage, MessageContext context);
}