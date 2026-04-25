package com.marmitt.core.ports.outbound.repository;

import com.marmitt.core.domain.portfolio.DeadLetterEntry;

public interface DeadLetterReprocessingPort {

    boolean supports(DeadLetterEntry entry);

    void reprocess(DeadLetterEntry entry);
}
