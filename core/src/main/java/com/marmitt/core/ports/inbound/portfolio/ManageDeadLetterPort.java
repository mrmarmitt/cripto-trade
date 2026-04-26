package com.marmitt.core.ports.inbound.portfolio;

import com.marmitt.core.dto.portfolio.response.DeadLetterEntryDto;
import com.marmitt.core.dto.portfolio.response.ReprocessDeadLetterResponse;
import com.marmitt.core.dto.portfolio.response.ResolveDeadLetterResponse;

import java.util.List;
import java.util.UUID;

public interface ManageDeadLetterPort {

    List<DeadLetterEntryDto> listUnresolved(UUID portfolioId, UUID runnerId, int limit);

    ResolveDeadLetterResponse resolve(UUID deadLetterId, String resolvedBy, String resolutionNote);

    ReprocessDeadLetterResponse reprocess(UUID deadLetterId, String requestedBy, String resolutionNote);
}

