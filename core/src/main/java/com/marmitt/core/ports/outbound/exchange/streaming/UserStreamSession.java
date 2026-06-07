package com.marmitt.core.ports.outbound.exchange.streaming;

import java.io.IOException;
import java.util.Optional;

public interface UserStreamSession {

    String open() throws IOException;

    void close();

    default Optional<String> subscriptionMessage() {
        return Optional.empty();
    }
}
