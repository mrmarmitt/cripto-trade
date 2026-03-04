package com.marmitt.application.spring.infrastructure.persistence.adapter;

import com.marmitt.application.spring.infrastructure.persistence.mapper.DeadLetterEntryEntityMapper;
import com.marmitt.application.spring.infrastructure.persistence.repository.DeadLetterEntryJdbcRepository;
import com.marmitt.core.domain.portfolio.DeadLetterEntry;
import com.marmitt.core.enums.DlqReason;
import com.marmitt.core.ports.outbound.repository.DeadLetterEntryRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class JdbcDeadLetterEntryRepositoryAdapter implements DeadLetterEntryRepositoryPort {

    private final DeadLetterEntryJdbcRepository deadLetterEntryJdbcRepository;

    @Override
    @Transactional
    public void save(DeadLetterEntry entry) {
        long start = System.nanoTime();
        deadLetterEntryJdbcRepository.save(DeadLetterEntryEntityMapper.toEntity(entry));
        log.trace("[REPO] deadLetterEntry.save({}) - {}ms", entry.getId(), RepoTiming.elapsedMs(start));
    }

    @Override
    public boolean existsUnresolvedByPortfolioId(UUID portfolioId) {
        long start = System.nanoTime();
        boolean exists = deadLetterEntryJdbcRepository.existsUnresolvedByPortfolioId(portfolioId);
        log.trace("[REPO] deadLetterEntry.existsUnresolvedByPortfolioId({}) - {}ms - exists={}",
                portfolioId, RepoTiming.elapsedMs(start), exists);
        return exists;
    }

    @Override
    public boolean existsUnresolvedByPortfolioIdAndRunnerIsNull(UUID portfolioId) {
        long start = System.nanoTime();
        boolean exists = deadLetterEntryJdbcRepository.existsUnresolvedByPortfolioIdAndRunnerIsNull(portfolioId);
        log.trace("[REPO] deadLetterEntry.existsUnresolvedByPortfolioIdAndRunnerIsNull({}) - {}ms - exists={}",
                portfolioId, RepoTiming.elapsedMs(start), exists);
        return exists;
    }

    @Override
    public boolean existsUnresolvedByRunnerId(UUID runnerId) {
        long start = System.nanoTime();
        boolean exists = deadLetterEntryJdbcRepository.existsUnresolvedByRunnerId(runnerId);
        log.trace("[REPO] deadLetterEntry.existsUnresolvedByRunnerId({}) - {}ms - exists={}",
                runnerId, RepoTiming.elapsedMs(start), exists);
        return exists;
    }

    @Override
    public boolean existsUnresolvedByIdentity(UUID portfolioId,
                                              UUID runnerId,
                                              String clientOrderId,
                                              String exchangeOrderId,
                                              DlqReason reason) {
        long start = System.nanoTime();
        boolean exists = deadLetterEntryJdbcRepository.existsUnresolvedByIdentity(
                portfolioId,
                runnerId,
                clientOrderId,
                exchangeOrderId,
                reason
        );
        log.trace("[REPO] deadLetterEntry.existsUnresolvedByIdentity({}, {}, {}, {}, {}) - {}ms - exists={}",
                portfolioId, runnerId, clientOrderId, exchangeOrderId, reason, RepoTiming.elapsedMs(start), exists);
        return exists;
    }
}
