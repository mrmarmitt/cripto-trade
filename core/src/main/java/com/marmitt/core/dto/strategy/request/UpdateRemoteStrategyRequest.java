package com.marmitt.core.dto.strategy.request;

/**
 * Troca de endereco e/ou token de uma estrategia remota (T54). Campo nulo mantem o valor atual; token
 * vazio remove o token.
 *
 * @param reason motivo da mudanca (obrigatorio)
 */
public record UpdateRemoteStrategyRequest(String baseUrl, String token, String reason) {
}
