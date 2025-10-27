package com.marmitt.core.ports.outbound.listener;

import com.marmitt.core.domain.data.OrderData;

public interface OrderUpdateListener {

    void onOrderUpdate(OrderData orderData);
}