package com.marmitt.core.dto.strategy.request;

/**
 * Habilitar ou desabilitar uma estrategia remota (T54).
 *
 * @param reason motivo da mudanca (obrigatorio)
 */
public record ChangeRemoteStrategyStatusRequest(String reason) {
}
