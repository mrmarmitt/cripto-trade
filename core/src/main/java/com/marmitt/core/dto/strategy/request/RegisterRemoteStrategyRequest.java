package com.marmitt.core.dto.strategy.request;

import java.util.UUID;

/**
 * Cadastro de uma estrategia remota (T54). Repetir o mesmo cadastro devolve o registro existente.
 *
 * @param strategyRef identidade da versao ({@code strategyId} usado pelos runners e servido em {@code /v1/meta})
 * @param baseUrl     URL base do contrato, ex.: {@code http://host:8090/v1}
 * @param token       token da estrategia, enviado como {@code Authorization: Bearer} (opcional)
 */
public record RegisterRemoteStrategyRequest(UUID strategyRef, String name, String version, String baseUrl,
                                            String token) {
}
