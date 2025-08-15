package com.marmitt.ctrade.domain.listener;

import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;

public interface PriceUpdateListener {
    
    void onPriceUpdate(PriceUpdateMessage priceUpdate);
}