package com.marmitt.core.ports.outbound.repository;

import com.marmitt.core.domain.portfolio.DeadLetterEntry;

/**
 * Outbound port for DeadLetterEntry persistence.
 */
public interface DeadLetterEntryRepositoryPort {

    void save(DeadLetterEntry entry);
}
