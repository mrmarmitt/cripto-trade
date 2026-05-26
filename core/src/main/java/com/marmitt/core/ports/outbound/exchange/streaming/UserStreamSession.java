package com.marmitt.core.ports.outbound.exchange.streaming;

import java.io.IOException;

public interface UserStreamSession {

    String open() throws IOException;

    void close();
}
