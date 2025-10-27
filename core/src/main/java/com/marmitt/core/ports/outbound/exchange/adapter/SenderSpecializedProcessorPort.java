package com.marmitt.core.ports.outbound.exchange.adapter;

import com.marmitt.core.dto.websocket.request.MessageRequest;
import com.marmitt.core.enums.MessageType;

public interface SenderSpecializedProcessorPort {

    String execute(MessageRequest request);

    boolean canProcess(MessageType messageType);
}
