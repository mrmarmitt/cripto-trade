package com.marmitt.core.ports.outbound.exchange.adapter;

import com.marmitt.core.dto.websocket.request.SendMessageRequest;
import com.marmitt.core.enums.MessageType;

public interface SenderSpecializedProcessorPort {
    String execute(SendMessageRequest request);
    boolean canProcess(MessageType messageType);
}
