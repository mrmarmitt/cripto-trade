package com.marmitt.core.ports.outbound.exchange.streaming;

import java.util.UUID;

public interface UserStreamSessionPort {

    String getExchangeName();

    UserStreamSession createSession(UUID connectionId);
}
