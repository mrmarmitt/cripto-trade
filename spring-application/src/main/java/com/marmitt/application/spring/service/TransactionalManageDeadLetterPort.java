package com.marmitt.application.spring.service;

import com.marmitt.core.dto.portfolio.DeadLetterEntryDto;
import com.marmitt.core.dto.portfolio.ReprocessDeadLetterResponse;
import com.marmitt.core.dto.portfolio.ResolveDeadLetterResponse;
import com.marmitt.core.ports.inbound.portfolio.ManageDeadLetterPort;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public class TransactionalManageDeadLetterPort implements ManageDeadLetterPort {

    private final ManageDeadLetterPort delegate;

    public TransactionalManageDeadLetterPort(ManageDeadLetterPort delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeadLetterEntryDto> listUnresolved(UUID portfolioId, UUID runnerId, int limit) {
        return delegate.listUnresolved(portfolioId, runnerId, limit);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResolveDeadLetterResponse resolve(UUID deadLetterId, String resolvedBy, String resolutionNote) {
        return delegate.resolve(deadLetterId, resolvedBy, resolutionNote);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReprocessDeadLetterResponse reprocess(UUID deadLetterId, String requestedBy, String resolutionNote) {
        return delegate.reprocess(deadLetterId, requestedBy, resolutionNote);
    }
}
