package com.marmitt.core.ports.outbound.exchange.adapter;

import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.ProcessorResponse;

public interface ReceivedSpecializedProcessorPort<T extends ProcessorResponse> {

    ProcessingResult<T> processMessage(String rawMessage, MessageContext context);

    boolean canProcess(String rawMessage);
}
