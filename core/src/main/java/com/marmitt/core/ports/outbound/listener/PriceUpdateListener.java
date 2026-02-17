package com.marmitt.core.ports.outbound.listener;

import com.marmitt.core.dto.websocket.data.MarketDataDto;

public interface PriceUpdateListener {

    void onPriceUpdate(MarketDataDto marketData);
}