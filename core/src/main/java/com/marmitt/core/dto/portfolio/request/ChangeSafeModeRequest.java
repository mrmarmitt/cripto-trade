package com.marmitt.core.dto.portfolio.request;

import com.marmitt.core.enums.SafeModeStatus;

/**
 * Pedido de mudança de nível do Safe Mode (T45). {@code confirm} é obrigatório ao subir para
 * {@code CANCEL_ALL} ou {@code PANIC_SELL}; {@code reason} é sempre obrigatório (auditoria).
 */
public record ChangeSafeModeRequest(
        SafeModeStatus level,
        String reason,
        boolean confirm
) {
}
