package com.marmitt.ctrade.domain.listener;

import com.marmitt.ctrade.domain.dto.OrderUpdateMessage;

public interface OrderUpdateListener {
    
    void onOrderUpdate(OrderUpdateMessage orderUpdate);
}