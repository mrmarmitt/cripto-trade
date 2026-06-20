package com.marmitt.core.application.usecase.portfolio;

import com.marmitt.core.domain.portfolio.DeadLetterEntry;
import com.marmitt.core.dto.portfolio.response.DeadLetterEntryDto;
import com.marmitt.core.dto.portfolio.DeadLetterReprocessingResult;
import com.marmitt.core.dto.portfolio.response.ReprocessDeadLetterResponse;
import com.marmitt.core.dto.portfolio.response.ResolveDeadLetterResponse;
import com.marmitt.core.exceptions.DeadLetterConflictException;
import com.marmitt.core.exceptions.DeadLetterNotFoundException;
import com.marmitt.core.ports.inbound.portfolio.ManageDeadLetterPort;
import com.marmitt.core.ports.outbound.repository.DeadLetterReprocessingPort;
import com.marmitt.core.ports.outbound.repository.DeadLetterEntryRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;

@Slf4j
public class ManageDeadLetterUseCase implements ManageDeadLetterPort {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final DeadLetterEntryRepositoryPort deadLetterEntryRepository;
    private final DeadLetterReprocessingPort deadLetterReprocessingPort;

    public ManageDeadLetterUseCase(DeadLetterEntryRepositoryPort deadLetterEntryRepository,
                                   DeadLetterReprocessingPort deadLetterReprocessingPort) {
        this.deadLetterEntryRepository = deadLetterEntryRepository;
        this.deadLetterReprocessingPort = deadLetterReprocessingPort;
    }

    @Override
    public List<DeadLetterEntryDto> listUnresolved(UUID portfolioId, UUID runnerId, int limit) {
        int boundedLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        log.debug("deadLetter: listing unresolved portfolioId={} runnerId={} limit={}",
                portfolioId, runnerId, boundedLimit);

        return deadLetterEntryRepository.findUnresolved(portfolioId, runnerId, boundedLimit).stream()
                .map(DeadLetterEntryDto::fromDomain)
                .toList();
    }

    @Override
    public ResolveDeadLetterResponse resolve(UUID deadLetterId, String resolvedBy, String resolutionNote) {
        if (resolvedBy == null || resolvedBy.isBlank()) {
            throw new IllegalArgumentException("resolvedBy cannot be blank");
        }

        DeadLetterEntry entry = deadLetterEntryRepository.findById(deadLetterId)
                .orElseThrow(() -> new DeadLetterNotFoundException(deadLetterId));

        if (entry.isResolved()) {
            throw new DeadLetterConflictException(deadLetterId, "Dead letter entry is already resolved");
        }

        entry.resolve(resolvedBy);
        deadLetterEntryRepository.save(entry);

        log.info("deadLetter: resolved id={} portfolioId={} runnerId={} resolvedBy={} note={}",
                entry.getId(), entry.getPortfolioId(), entry.getRunnerId(), resolvedBy, resolutionNote);

        return ResolveDeadLetterResponse.success(
                DeadLetterEntryDto.fromDomain(entry),
                "Dead letter entry resolved successfully"
        );
    }

    @Override
    public ReprocessDeadLetterResponse reprocess(UUID deadLetterId, String requestedBy, String resolutionNote) {
        if (requestedBy == null || requestedBy.isBlank()) {
            throw new IllegalArgumentException("requestedBy cannot be blank");
        }

        DeadLetterEntry entry = deadLetterEntryRepository.findById(deadLetterId)
                .orElseThrow(() -> new DeadLetterNotFoundException(deadLetterId));

        if (entry.isResolved()) {
            throw new DeadLetterConflictException(deadLetterId, "Dead letter entry is already resolved");
        }

        if (!deadLetterReprocessingPort.supports(entry)) {
            throw new DeadLetterConflictException(
                    deadLetterId, "Dead letter entry cannot be reprocessed automatically");
        }

        DeadLetterReprocessingResult reprocessingResult = deadLetterReprocessingPort.reprocess(entry);
        if (!reprocessingResult.applied()) {
            throw new DeadLetterConflictException(deadLetterId, reprocessingResult.message());
        }

        entry.resolve(requestedBy);
        deadLetterEntryRepository.save(entry);

        log.info("deadLetter: reprocessed id={} portfolioId={} runnerId={} requestedBy={} note={}",
                entry.getId(), entry.getPortfolioId(), entry.getRunnerId(), requestedBy, resolutionNote);

        return ReprocessDeadLetterResponse.success(
                DeadLetterEntryDto.fromDomain(entry),
                "Dead letter entry reprocessed successfully"
        );
    }
}

