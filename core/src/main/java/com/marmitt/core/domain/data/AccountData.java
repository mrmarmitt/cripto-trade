package com.marmitt.core.domain.data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Representa dados de conta processados de mensagens WebSocket
 */
public record AccountData(
    String accountId,
    Map<String, BigDecimal> balances, // asset -> balance
    Map<String, BigDecimal> lockedBalances, // asset -> locked amount
    Instant lastUpdateTime
) implements ProcessorResponse {}