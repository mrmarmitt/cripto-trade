package com.marmitt.binance.processor.send;

import com.marmitt.core.dto.websocket.request.MessageRequest;
import com.marmitt.core.enums.MessageType;

interface BinanceSenderProcessor {

    String execute(MessageRequest request);

    boolean canProcess(MessageType messageType);
}
