package com.marmitt.core.ports.outbound.exchange.streaming;

import java.io.IOException;
import java.util.UUID;

public interface UserStreamSessionPort {

    String getExchangeName();

    String openSession(UUID connectionId) throws IOException;

    void closeSession(UUID connectionId);
}
