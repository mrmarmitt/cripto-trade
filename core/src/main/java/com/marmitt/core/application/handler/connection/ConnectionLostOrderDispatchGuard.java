package com.marmitt.core.application.handler.connection;

import com.marmitt.core.dto.events.WebSocketClosedEvent;
import com.marmitt.core.dto.events.WebSocketConnectedEvent;
import com.marmitt.core.enums.StreamChannel;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConnectionLostOrderDispatchGuard {

    private final ExchangeAdapterRepositoryPort adapterRepository;

    public ConnectionLostOrderDispatchGuard(ExchangeAdapterRepositoryPort adapterRepository) {
        this.adapterRepository = adapterRepository;
    }

    public void onConnectionClosed(WebSocketClosedEvent event) {
        if (event.wasExpected()) {
            return;
        }
        StreamChannel channel = event.channel();
        if (channel != StreamChannel.MARKET && channel != StreamChannel.USER_DATA) {
            return;
        }
        log.warn("Unexpected connection loss on exchange={} channel={} — blocking order dispatch",
                event.exchange(), channel);
        adapterRepository.blockDispatch(event.exchange());
    }

    public void onConnectionReestablished(WebSocketConnectedEvent event) {
        if (!event.wasReconnection()) {
            return;
        }
        log.info("Connection reestablished on exchange={} channel={} — unblocking order dispatch",
                event.exchange(), event.channel());
        adapterRepository.unblockDispatch(event.exchange());
    }
}
