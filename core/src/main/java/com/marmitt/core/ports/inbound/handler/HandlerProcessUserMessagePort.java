package com.marmitt.core.ports.inbound.handler;

import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;

public interface HandlerProcessUserMessagePort {

    ProcessingResult<?> execute(String rawMessage, MessageContext context);
}
