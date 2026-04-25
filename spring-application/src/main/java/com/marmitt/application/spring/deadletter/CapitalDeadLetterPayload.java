package com.marmitt.application.spring.deadletter;

import com.marmitt.core.enums.FeeType;
import com.marmitt.core.enums.ReleaseReason;

import java.math.BigDecimal;
import java.util.UUID;

record CapitalDeadLetterPayload(
        String eventType,
        UUID runnerId,
        UUID transactionId,
        UUID matchId,
        BigDecimal executedQuantity,
        BigDecimal executedPrice,
        BigDecimal feeAmount,
        String feeAsset,
        FeeType feeType,
        BigDecimal feeConvertedAmount,
        BigDecimal totalCost,
        BigDecimal pnlRealized,
        Boolean isFinal,
        BigDecimal releaseAmount,
        ReleaseReason reason,
        BigDecimal executedAmount,
        String failureType,
        String failureMessage
) {
}
