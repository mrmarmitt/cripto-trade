package com.marmitt.core.ports.outbound.repository;

import com.marmitt.core.domain.portfolio.DeadLetterEntry;
import com.marmitt.core.enums.DlqReason;

import java.util.UUID;

/**
 * Outbound port for DeadLetterEntry persistence.
 */
public interface DeadLetterEntryRepositoryPort {

    void save(DeadLetterEntry entry);

    boolean existsUnresolvedByPortfolioId(UUID portfolioId);

    boolean existsUnresolvedByPortfolioIdAndRunnerIsNull(UUID portfolioId);

    boolean existsUnresolvedByRunnerId(UUID runnerId);

    boolean existsUnresolvedByIdentity(UUID portfolioId,
                                       UUID runnerId,
                                       String clientOrderId,
                                       String exchangeOrderId,
                                       DlqReason reason);
}
