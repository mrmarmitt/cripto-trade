package com.marmitt.core.ports.outbound.connection;

import com.marmitt.core.enums.StreamChannel;

import java.util.UUID;

public interface ReconnectionStrategyPort {

    void scheduleReconnect(String exchangeName, StreamChannel channel, UUID sessionToClose, int attempt);
}
