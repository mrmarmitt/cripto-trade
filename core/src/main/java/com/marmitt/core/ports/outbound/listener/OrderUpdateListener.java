package com.marmitt.core.ports.outbound.listener;

import com.marmitt.core.dto.websocket.data.OrderDataDto;

public interface OrderUpdateListener {

    void onOrderUpdate(OrderDataDto orderData);

    boolean shouldProcess(OrderDataDto orderData);
}