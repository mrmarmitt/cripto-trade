package com.marmitt.core.ports.outbound.listener;

import com.marmitt.core.dto.websocket.data.OrderDataDto;

public interface OrderUpdateListener {

    void onOrderUpdate(OrderDataDto orderData);

    default boolean shouldProcess(OrderDataDto orderData) {
        return true;
    }
}