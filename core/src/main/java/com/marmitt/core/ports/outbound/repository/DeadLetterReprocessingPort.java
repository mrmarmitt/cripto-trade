package com.marmitt.core.ports.outbound.repository;

import com.marmitt.core.domain.portfolio.DeadLetterEntry;
import com.marmitt.core.dto.portfolio.DeadLetterReprocessingResult;

public interface DeadLetterReprocessingPort {

    boolean supports(DeadLetterEntry entry);

    DeadLetterReprocessingResult reprocess(DeadLetterEntry entry);
}
