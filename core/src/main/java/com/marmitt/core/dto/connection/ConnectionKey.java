package com.marmitt.core.dto.connection;

import com.marmitt.core.enums.StreamChannel;

public record ConnectionKey(String exchangeName, StreamChannel channel) {

    public static ConnectionKey market(String exchangeName) {
        return new ConnectionKey(exchangeName, StreamChannel.MARKET);
    }

    public static ConnectionKey userStream(String exchangeName) {
        return new ConnectionKey(exchangeName, StreamChannel.USER_DATA);
    }
}
