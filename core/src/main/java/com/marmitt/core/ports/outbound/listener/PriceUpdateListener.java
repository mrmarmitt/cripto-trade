package com.marmitt.core.ports.outbound.listener;

import com.marmitt.core.domain.data.MarketData;

public interface PriceUpdateListener {

    void onPriceUpdate(MarketData marketData);
}