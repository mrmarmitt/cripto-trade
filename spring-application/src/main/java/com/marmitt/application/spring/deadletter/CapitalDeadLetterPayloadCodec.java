package com.marmitt.application.spring.deadletter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.domain.shared.Fee;
import com.marmitt.core.dto.capital.ExecutionConfirmation;
import com.marmitt.core.dto.capital.MarginRelease;
import com.marmitt.core.dto.events.ExecutionConfirmedEvent;
import com.marmitt.core.dto.events.MarginReleaseEvent;
import org.springframework.stereotype.Component;

@Component
public class CapitalDeadLetterPayloadCodec {

    private static final String EXECUTION_CONFIRMED = "EXECUTION_CONFIRMED";
    private static final String MARGIN_RELEASE = "MARGIN_RELEASE";

    private final ObjectMapper objectMapper;

    public CapitalDeadLetterPayloadCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encodeExecutionConfirmed(ExecutionConfirmedEvent event, Exception failure) {
        ExecutionConfirmation confirmation = event.confirmation();
        return write(new CapitalDeadLetterPayload(
                EXECUTION_CONFIRMED,
                confirmation.runnerId(),
                confirmation.transactionId(),
                confirmation.matchId(),
                confirmation.executedQuantity(),
                confirmation.executedPrice(),
                confirmation.fee().amount(),
                confirmation.fee().asset(),
                confirmation.fee().type(),
                confirmation.fee().convertedAmount(),
                confirmation.totalCost(),
                confirmation.pnlRealized(),
                confirmation.isFinal(),
                null,
                null,
                null,
                failure.getClass().getSimpleName(),
                safeMessage(failure)
        ));
    }

    public String encodeMarginRelease(MarginReleaseEvent event, Exception failure) {
        MarginRelease release = event.release();
        return write(new CapitalDeadLetterPayload(
                MARGIN_RELEASE,
                release.runnerId(),
                release.transactionId(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                release.releaseAmount(),
                release.reason(),
                release.executedAmount(),
                failure.getClass().getSimpleName(),
                safeMessage(failure)
        ));
    }

    public CapitalDeadLetterPayload decode(String rawPayload) {
        try {
            return objectMapper.readValue(rawPayload, CapitalDeadLetterPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid capital DLQ payload", e);
        }
    }

    public boolean isExecutionConfirmed(CapitalDeadLetterPayload payload) {
        return EXECUTION_CONFIRMED.equals(payload.eventType());
    }

    public boolean isMarginRelease(CapitalDeadLetterPayload payload) {
        return MARGIN_RELEASE.equals(payload.eventType());
    }

    public ExecutionConfirmedEvent toExecutionConfirmedEvent(CapitalDeadLetterPayload payload) {
        return new ExecutionConfirmedEvent(new ExecutionConfirmation(
                payload.transactionId(),
                payload.runnerId(),
                payload.matchId(),
                payload.executedQuantity(),
                payload.executedPrice(),
                toFee(payload),
                payload.totalCost(),
                payload.pnlRealized(),
                Boolean.TRUE.equals(payload.isFinal())
        ));
    }

    public MarginReleaseEvent toMarginReleaseEvent(CapitalDeadLetterPayload payload) {
        return new MarginReleaseEvent(new MarginRelease(
                payload.transactionId(),
                payload.runnerId(),
                payload.releaseAmount(),
                payload.reason(),
                payload.executedAmount()
        ));
    }

    private Fee toFee(CapitalDeadLetterPayload payload) {
        if (payload.feeAsset() == null || payload.feeType() == null) {
            return Fee.zero("USDT");
        }
        if (payload.feeConvertedAmount() == null) {
            return Fee.pendingConversion(payload.feeAmount(), payload.feeAsset(), payload.feeType());
        }
        return new Fee(payload.feeAmount(), payload.feeAsset(), payload.feeType(), payload.feeConvertedAmount());
    }

    private String write(CapitalDeadLetterPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize capital DLQ payload", e);
        }
    }

    private static String safeMessage(Throwable throwable) {
        return throwable.getMessage() != null ? throwable.getMessage() : "<no-message>";
    }
}
