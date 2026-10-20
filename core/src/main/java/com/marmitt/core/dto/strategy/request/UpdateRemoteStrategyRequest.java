package com.marmitt.core.dto.strategy.request;

/**
 * Troca de endereco e/ou token de uma estrategia remota (T54). Campo nulo mantem o valor atual; token
 * vazio remove o token.
 */
public record UpdateRemoteStrategyRequest(String baseUrl, String token) {
}
