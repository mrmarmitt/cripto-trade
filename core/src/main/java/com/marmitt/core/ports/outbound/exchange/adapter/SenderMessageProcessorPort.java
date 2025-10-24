package com.marmitt.core.ports.outbound.exchange.adapter;

import com.marmitt.core.dto.websocket.request.MessageRequest;

public interface SenderMessageProcessorPort {
    
    String execute(MessageRequest request);
}
