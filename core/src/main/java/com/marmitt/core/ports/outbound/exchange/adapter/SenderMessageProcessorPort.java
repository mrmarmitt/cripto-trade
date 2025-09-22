package com.marmitt.core.ports.outbound.exchange.adapter;

import com.marmitt.core.dto.websocket.request.SendMessageRequest;

public interface SenderMessageProcessorPort {
    
    String execute(SendMessageRequest request);
}
