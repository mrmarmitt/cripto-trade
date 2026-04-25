package com.marmitt.application.spring.deadletter;

import com.marmitt.core.application.reaction.ExecutionConfirmedReaction;
import com.marmitt.core.application.reaction.MarginReleasedReaction;
import com.marmitt.core.domain.portfolio.DeadLetterEntry;
import com.marmitt.core.dto.portfolio.CapitalDeadLetterPayload;
import com.marmitt.core.enums.DlqReason;
import com.marmitt.core.ports.outbound.repository.DeadLetterReprocessingPort;
import org.springframework.stereotype.Component;

@Component
public class CapitalDeadLetterReprocessingAdapter implements DeadLetterReprocessingPort {

    private final ExecutionConfirmedReaction executionConfirmedReaction;
    private final MarginReleasedReaction marginReleasedReaction;
    private final CapitalDeadLetterPayloadCodec payloadCodec;

    public CapitalDeadLetterReprocessingAdapter(ExecutionConfirmedReaction executionConfirmedReaction,
                                                MarginReleasedReaction marginReleasedReaction,
                                                CapitalDeadLetterPayloadCodec payloadCodec) {
        this.executionConfirmedReaction = executionConfirmedReaction;
        this.marginReleasedReaction = marginReleasedReaction;
        this.payloadCodec = payloadCodec;
    }

    @Override
    public boolean supports(DeadLetterEntry entry) {
        if (entry.getReason() != DlqReason.RETRY_EXHAUSTED) {
            return false;
        }

        try {
            CapitalDeadLetterPayload payload = payloadCodec.decode(entry.getRawPayload());
            return payloadCodec.isExecutionConfirmed(payload) || payloadCodec.isMarginRelease(payload);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public void reprocess(DeadLetterEntry entry) {
        CapitalDeadLetterPayload payload = payloadCodec.decode(entry.getRawPayload());
        if (payloadCodec.isExecutionConfirmed(payload)) {
            executionConfirmedReaction.handle(payloadCodec.toExecutionConfirmedEvent(payload));
            return;
        }
        if (payloadCodec.isMarginRelease(payload)) {
            marginReleasedReaction.handle(payloadCodec.toMarginReleaseEvent(payload));
            return;
        }
        throw new IllegalArgumentException("Unsupported capital DLQ payload eventType=" + payload.eventType());
    }
}
