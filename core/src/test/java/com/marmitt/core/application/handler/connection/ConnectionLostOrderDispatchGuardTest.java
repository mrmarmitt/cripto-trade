package com.marmitt.core.application.handler.connection;

import com.marmitt.core.dto.events.WebSocketClosedEvent;
import com.marmitt.core.dto.events.WebSocketConnectedEvent;
import com.marmitt.core.enums.StreamChannel;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeAccountQueryPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeBootReadinessPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderExecutionPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderQueryPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeStreamingPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeUserStreamPort;
import com.marmitt.core.ports.outbound.exchange.streaming.UserStreamSession;
import com.marmitt.core.ports.outbound.exchange.streaming.UserStreamSessionPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionLostOrderDispatchGuardTest {

    private TrackingAdapterRepository adapterRepository;
    private ConnectionLostOrderDispatchGuard guard;

    @BeforeEach
    void setup() {
        adapterRepository = new TrackingAdapterRepository();
        guard = new ConnectionLostOrderDispatchGuard(adapterRepository);
    }

    @Test
    void unexpectedMarketClose_blocksDispatch() {
        WebSocketClosedEvent event = WebSocketClosedEvent.unexpected(
                "BINANCE", 1006, "Connection reset", UUID.randomUUID(), StreamChannel.MARKET);

        guard.onConnectionClosed(event);

        assertTrue(adapterRepository.isDispatchBlocked("BINANCE"));
    }

    @Test
    void unexpectedUserDataClose_blocksDispatch() {
        WebSocketClosedEvent event = WebSocketClosedEvent.unexpected(
                "BINANCE", 1006, "Connection reset", UUID.randomUUID(), StreamChannel.USER_DATA);

        guard.onConnectionClosed(event);

        assertTrue(adapterRepository.isDispatchBlocked("BINANCE"));
    }

    @Test
    void expectedClose_doesNotBlockDispatch() {
        WebSocketClosedEvent event = WebSocketClosedEvent.of(
                "BINANCE", 1000, "Normal closure", UUID.randomUUID(), StreamChannel.MARKET);

        guard.onConnectionClosed(event);

        assertFalse(adapterRepository.isDispatchBlocked("BINANCE"));
    }

    @Test
    void reconnection_unblocksPreviouslyBlockedDispatch() {
        adapterRepository.blockDispatch("BINANCE");
        assertTrue(adapterRepository.isDispatchBlocked("BINANCE"));

        WebSocketConnectedEvent event = WebSocketConnectedEvent.reconnection(
                "BINANCE", "Reconnected", UUID.randomUUID(), StreamChannel.MARKET);

        guard.onConnectionReestablished(event);

        assertFalse(adapterRepository.isDispatchBlocked("BINANCE"));
    }

    @Test
    void initialConnection_doesNotUnblockDispatch() {
        adapterRepository.blockDispatch("BINANCE");

        WebSocketConnectedEvent event = WebSocketConnectedEvent.of(
                "BINANCE", "Connected", UUID.randomUUID(), StreamChannel.MARKET);

        guard.onConnectionReestablished(event);

        assertTrue(adapterRepository.isDispatchBlocked("BINANCE"), "initial connection must not change dispatch block state");
    }

    static class TrackingAdapterRepository implements ExchangeAdapterRepositoryPort {
        private final Set<String> blocked = ConcurrentHashMap.newKeySet();

        @Override public void blockDispatch(String exchangeName) { blocked.add(exchangeName.toUpperCase()); }
        @Override public void unblockDispatch(String exchangeName) { blocked.remove(exchangeName.toUpperCase()); }
        @Override public boolean isDispatchBlocked(String exchangeName) { return blocked.contains(exchangeName.toUpperCase()); }

        @Override public void registerStreamingAdapter(ExchangeStreamingPort a) {}
        @Override public void registerUserStreamAdapter(ExchangeUserStreamPort a) {}
        @Override public void registerUserStreamSession(UserStreamSessionPort s) {}
        @Override public void storeActiveSession(UUID id, UserStreamSession s) {}
        @Override public void registerOrderExecutionAdapter(String n, ExchangeOrderExecutionPort a) {}
        @Override public void registerOrderQueryAdapter(String n, ExchangeOrderQueryPort a) {}
        @Override public void registerAccountQueryAdapter(String n, ExchangeAccountQueryPort a) {}
        @Override public void registerBootReadinessAdapter(String n, ExchangeBootReadinessPort a) {}
        @Override public void registerPortfolioByAdapter(String n, UUID id) {}
        @Override public boolean hasAdapter(String n) { return false; }
        @Override public Set<String> getAllExchangeNames() { return Set.of(); }
        @Override public int getAdapterCount() { return 0; }
        @Override public Optional<ExchangeStreamingPort> findStreamingByName(String n) { return Optional.empty(); }
        @Override public Optional<ExchangeOrderExecutionPort> findOrderExecutionByName(String n) { return Optional.empty(); }
        @Override public Optional<ExchangeOrderQueryPort> findOrderQueryByName(String n) { return Optional.empty(); }
        @Override public Optional<ExchangeAccountQueryPort> findAccountQueryByName(String n) { return Optional.empty(); }
        @Override public Optional<ExchangeBootReadinessPort> findBootReadinessByName(String n) { return Optional.empty(); }
        @Override public Optional<ExchangeUserStreamPort> findUserStreamByName(String n) { return Optional.empty(); }
        @Override public Optional<UserStreamSessionPort> findUserStreamSessionByName(String n) { return Optional.empty(); }
        @Override public Optional<UserStreamSession> findActiveSession(UUID id) { return Optional.empty(); }
        @Override public void removeActiveSession(UUID id) {}
    }
}
