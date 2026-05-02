package com.marmitt.coinbase.processor.send;

import com.marmitt.core.dto.websocket.request.MessageRequest;
import com.marmitt.core.enums.MessageType;

interface CoinbaseSenderProcessor {

    String execute(MessageRequest request);

    boolean canProcess(MessageType messageType);
}
