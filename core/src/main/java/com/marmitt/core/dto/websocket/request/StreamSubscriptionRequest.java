package com.marmitt.core.dto.websocket.request;

import com.marmitt.core.dto.common.CurrencyPair;
import com.marmitt.core.enums.MessageType;
import com.marmitt.core.enums.StreamAction;
import lombok.Getter;

import java.util.List;

@Getter
public class StreamSubscriptionRequest extends MessageRequest {
    
    private final List<CurrencyPair> currencyPairs;
    private final StreamAction streamAction;
    
    public StreamSubscriptionRequest(String exchangeName, List<CurrencyPair> currencyPairs, StreamAction streamAction) {
        super(exchangeName, streamAction == StreamAction.SUBSCRIBE ? MessageType.STREAM_SUBSCRIPTION : MessageType.STREAM_UNSUBSCRIPTION);
        this.currencyPairs = currencyPairs;
        this.streamAction = streamAction;
    }

}