package com.marmitt.strategyservice;

import java.util.Objects;
import java.util.UUID;

/**
 * Onde mora uma versão de estratégia remota: URL base do contrato (ex.: {@code http://host:8090/v1})
 * e token de serviço (opcional, enviado como {@code Authorization: Bearer}).
 */
public record RemoteStrategyEndpoint(UUID strategyRef, String baseUrl, String token) {

    public RemoteStrategyEndpoint {
        Objects.requireNonNull(strategyRef, "strategyRef cannot be null");
        Objects.requireNonNull(baseUrl, "baseUrl cannot be null");
        baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
