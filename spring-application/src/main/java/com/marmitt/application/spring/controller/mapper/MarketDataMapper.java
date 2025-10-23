package com.marmitt.application.spring.controller.mapper;

import com.marmitt.application.spring.controller.dto.common.CurrencyPairRequest;
import com.marmitt.application.spring.controller.dto.market.MarketDataStreamRequest;
import com.marmitt.core.dto.common.CurrencyPair;
import com.marmitt.core.dto.websocket.request.StreamSubscriptionRequest;
import com.marmitt.core.enums.StreamAction;

import java.util.List;

public class MarketDataMapper {

    public static StreamSubscriptionRequest toSubscribeSendStreamRequest(MarketDataStreamRequest request) {
        List<CurrencyPair> coreCurrencyPairs = request.symbols().stream()
                .map(MarketDataMapper::toCoreCurrencyPair)
                .toList();

        return new StreamSubscriptionRequest(
                request.exchange(),
                coreCurrencyPairs,
                StreamAction.SUBSCRIBE
        );
    }

    public static StreamSubscriptionRequest toUnsubscribeSendStreamRequest(MarketDataStreamRequest request) {
        List<CurrencyPair> coreCurrencyPairs = request.symbols().stream()
                .map(MarketDataMapper::toCoreCurrencyPair)
                .toList();

        return new StreamSubscriptionRequest(
                request.exchange(),
                coreCurrencyPairs,
                StreamAction.UNSUBSCRIBE
        );
    }

    private static CurrencyPair toCoreCurrencyPair(CurrencyPairRequest controllerPair) {
        return new CurrencyPair(
                controllerPair.baseCurrency(),
                controllerPair.quoteCurrency(),
                controllerPair.streamType()
        );
    }
}