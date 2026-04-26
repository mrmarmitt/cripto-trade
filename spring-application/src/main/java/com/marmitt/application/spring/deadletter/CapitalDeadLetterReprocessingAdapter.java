package com.marmitt.application.spring.deadletter;

import com.marmitt.core.application.reaction.ExecutionConfirmedReaction;
import com.marmitt.core.application.reaction.MarginReleasedReaction;
import com.marmitt.core.domain.portfolio.DeadLetterEntry;
import com.marmitt.core.domain.portfolio.GlobalBalance;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.events.MarginReleaseEvent;
import com.marmitt.core.dto.portfolio.DeadLetterReprocessingResult;
import com.marmitt.core.dto.portfolio.CapitalDeadLetterPayload;
import com.marmitt.core.enums.DlqReason;
import com.marmitt.core.ports.outbound.repository.DeadLetterReprocessingPort;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import org.springframework.stereotype.Component;

@Component
public class CapitalDeadLetterReprocessingAdapter implements DeadLetterReprocessingPort {

    private final ExecutionConfirmedReaction executionConfirmedReaction;
    private final MarginReleasedReaction marginReleasedReaction;
    private final CapitalDeadLetterPayloadCodec payloadCodec;
    private final StrategyRunnerRepositoryPort runnerRepository;
    private final GlobalBalanceRepositoryPort globalBalanceRepository;

    public CapitalDeadLetterReprocessingAdapter(ExecutionConfirmedReaction executionConfirmedReaction,
                                                MarginReleasedReaction marginReleasedReaction,
                                                CapitalDeadLetterPayloadCodec payloadCodec,
                                                StrategyRunnerRepositoryPort runnerRepository,
                                                GlobalBalanceRepositoryPort globalBalanceRepository) {
        this.executionConfirmedReaction = executionConfirmedReaction;
        this.marginReleasedReaction = marginReleasedReaction;
        this.payloadCodec = payloadCodec;
        this.runnerRepository = runnerRepository;
        this.globalBalanceRepository = globalBalanceRepository;
    }

    @Override
    public boolean supports(DeadLetterEntry entry) {
        if (entry.getReason() != DlqReason.RETRY_EXHAUSTED) {
            return false;
        }

        try {
            CapitalDeadLetterPayload payload = payloadCodec.decode(entry.getRawPayload());
            if (payloadCodec.isExecutionConfirmed(payload)) {
                payloadCodec.toExecutionConfirmedEvent(payload);
                return true;
            }
            if (payloadCodec.isMarginRelease(payload)) {
                payloadCodec.toMarginReleaseEvent(payload);
                return true;
            }
            return false;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public DeadLetterReprocessingResult reprocess(DeadLetterEntry entry) {
        CapitalDeadLetterPayload payload = payloadCodec.decode(entry.getRawPayload());
        if (payloadCodec.isExecutionConfirmed(payload)) {
            executionConfirmedReaction.handle(payloadCodec.toExecutionConfirmedEvent(payload));
            return DeadLetterReprocessingResult.applied("Execution confirmed replay applied");
        }
        if (payloadCodec.isMarginRelease(payload)) {
            MarginReleaseEvent replay = payloadCodec.toMarginReleaseEvent(payload);
            if (!canApplyMarginRelease(replay)) {
                return DeadLetterReprocessingResult.notApplied(
                        "Dead letter replay produced no state change and requires manual review"
                );
            }
            marginReleasedReaction.handle(replay);
            return DeadLetterReprocessingResult.applied("Margin release replay applied");
        }
        throw new IllegalArgumentException("Unsupported capital DLQ payload eventType=" + payload.eventType());
    }

    private boolean canApplyMarginRelease(MarginReleaseEvent event) {
        StrategyRunner runner = runnerRepository.findById(event.release().runnerId())
                .orElseThrow(() -> new IllegalStateException("Runner not found: " + event.release().runnerId()));

        GlobalBalance balance = globalBalanceRepository.findByPortfolioId(runner.getPortfolioId())
                .orElseThrow(() -> new IllegalStateException(
                        "GlobalBalance not found for portfolio: " + runner.getPortfolioId()));

        return balance.getReservedBalance().compareTo(event.release().releaseAmount()) >= 0;
    }
}
