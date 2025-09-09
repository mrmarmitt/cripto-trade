package com.marmitt.core.ports.outbound.websocket;

import com.marmitt.core.domain.data.ProcessorResponse;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;

public interface SpecializedProcessor<T extends ProcessorResponse> {
    ProcessingResult<T> processMessage(String rawMessage, MessageContext context);
    boolean canProcess(String rawMessage);

}
