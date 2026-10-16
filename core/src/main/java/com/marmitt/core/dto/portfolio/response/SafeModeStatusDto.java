package com.marmitt.core.dto.portfolio.response;

import com.marmitt.core.enums.SafeModeOrigin;
import com.marmitt.core.enums.SafeModeStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Nível atual do Safe Mode e o que ainda falta para ele convergir (T45).
 *
 * @param restingOrders    ordens LIMIT em voo nos runners operacionais (canceladas a partir de CANCEL_ALL)
 * @param liquidatingOrders vendas MARKET da liquidação ainda em voo
 * @param openPositions    posições abertas nos runners operacionais (vendidas em PANIC_SELL)
 * @param idleRunners      runners do portfolio fora de operação (halted, pausados ou reconciliando):
 *                         o Safe Mode não age sobre eles até voltarem a operar
 */
public record SafeModeStatusDto(
        UUID portfolioId,
        SafeModeStatus level,
        SafeModeOrigin origin,
        String reason,
        Instant changedAt,
        int restingOrders,
        int liquidatingOrders,
        int openPositions,
        int idleRunners
) {
}
