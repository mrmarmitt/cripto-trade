package com.marmitt.core.ports.outbound.exchange.streaming;

public interface UserStreamSessionPort {

    String getExchangeName();

    String buildConnectionUrl(String credential);
}
