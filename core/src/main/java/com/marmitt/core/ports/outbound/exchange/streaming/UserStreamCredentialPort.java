package com.marmitt.core.ports.outbound.exchange.streaming;

import java.io.IOException;
import java.util.UUID;

public interface UserStreamCredentialPort {

    String getExchangeName();

    String obtain(UUID connectionId) throws IOException;

    void revoke(UUID connectionId);
}
