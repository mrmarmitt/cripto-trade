package com.marmitt.core.ports.outbound.exchange;

import com.marmitt.core.dto.exchange.OrderSubmissionResult;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;

public interface ExchangeOrderPort {

    String getExchangeName();

    OrderSubmissionResult submitOrder(SendOrderRequest request);
}
