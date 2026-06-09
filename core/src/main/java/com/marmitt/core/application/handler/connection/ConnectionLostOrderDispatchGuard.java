package com.marmitt.core.application.handler.connection;

import com.marmitt.core.dto.events.WebSocketClosedEvent;
import com.marmitt.core.dto.events.WebSocketConnectedEvent;
import com.marmitt.core.enums.StreamChannel;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ConnectionLostOrderDispatchGuard {

    private final ExchangeAdapterRepositoryPort adapterRepository;
    private final ConcurrentHashMap<String, Set<StreamChannel>> blockedChannels = new ConcurrentHashMap<>();

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
        String exchange = event.exchange();
        blockedChannels.computeIfAbsent(exchange, k -> ConcurrentHashMap.newKeySet()).add(channel);
        log.warn("Unexpected connection loss on exchange={} channel={} — blocking order dispatch",
                exchange, channel);
        adapterRepository.blockDispatch(exchange);
    }

    public void onConnectionReestablished(WebSocketConnectedEvent event) {
        String exchange = event.exchange();
        Set<StreamChannel> pending = blockedChannels.get(exchange);
        if (pending == null || !pending.remove(event.channel())) {
            return;
        }
        if (pending.isEmpty()) {
            blockedChannels.remove(exchange);
            log.info("All lost channels reconnected on exchange={} — unblocking order dispatch", exchange);
            adapterRepository.unblockDispatch(exchange);
        } else {
            log.info("Channel {} reconnected but {} still recovering on exchange={} — dispatch remains blocked",
                    event.channel(), pending, exchange);
        }
    }
}
