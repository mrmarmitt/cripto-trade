package com.marmitt.core.ports.outbound.repository;

import com.marmitt.core.domain.portfolio.DeadLetterEntry;
import com.marmitt.core.enums.DlqReason;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for DeadLetterEntry persistence.
 */
public interface DeadLetterEntryRepositoryPort {

    void save(DeadLetterEntry entry);

    Optional<DeadLetterEntry> findById(UUID id);

    List<DeadLetterEntry> findUnresolved(UUID portfolioId, UUID runnerId, int limit);

    boolean existsUnresolvedByPortfolioId(UUID portfolioId);

    boolean existsUnresolvedByPortfolioIdAndRunnerIsNull(UUID portfolioId);

    boolean existsUnresolvedByRunnerId(UUID runnerId);

    boolean existsUnresolvedByIdentity(UUID portfolioId,
                                       UUID runnerId,
                                       String clientOrderId,
                                       String exchangeOrderId,
                                       DlqReason reason);
}
