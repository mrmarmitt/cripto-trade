package com.marmitt.core.ports.outbound.exchange.adapter;

import com.marmitt.core.domain.data.ProcessorResponse;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;

public interface ReceivedMessageProcessorPort {

    ProcessingResult<? extends ProcessorResponse> processMessage(String rawMessage, MessageContext context);
}