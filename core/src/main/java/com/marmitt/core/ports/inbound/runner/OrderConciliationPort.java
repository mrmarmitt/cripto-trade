package com.marmitt.core.ports.inbound.runner;

import com.marmitt.core.dto.websocket.data.OrderDataDto;

public interface OrderConciliationPort {

    void execute(OrderDataDto orderData);
}
