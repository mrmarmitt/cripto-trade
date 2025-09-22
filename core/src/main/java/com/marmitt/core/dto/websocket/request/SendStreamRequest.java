package com.marmitt.core.dto.websocket.request;

import com.marmitt.core.dto.common.CurrencyPair;
import com.marmitt.core.enums.MessageType;
import lombok.Getter;

import java.util.List;

@Getter
public class SendStreamRequest extends SendMessageRequest {
    
    private final List<CurrencyPair> currencyPairs;
    private final boolean subscribe;
    
    public SendStreamRequest(String exchangeName, List<CurrencyPair> currencyPairs, boolean subscribe) {
        super(exchangeName, subscribe ? MessageType.STREAM_SUBSCRIPTION : MessageType.STREAM_UNSUBSCRIPTION);
        this.currencyPairs = currencyPairs;
        this.subscribe = subscribe;
    }

}