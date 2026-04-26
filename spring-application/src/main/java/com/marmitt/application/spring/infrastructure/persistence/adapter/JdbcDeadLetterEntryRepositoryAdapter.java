package com.marmitt.application.spring.infrastructure.persistence.adapter;

import com.marmitt.application.spring.infrastructure.persistence.mapper.DeadLetterEntryEntityMapper;
import com.marmitt.application.spring.infrastructure.persistence.repository.DeadLetterEntryJdbcRepository;
import com.marmitt.core.domain.portfolio.DeadLetterEntry;
import com.marmitt.core.enums.DlqReason;
import com.marmitt.core.ports.outbound.repository.DeadLetterEntryRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class JdbcDeadLetterEntryRepositoryAdapter implements DeadLetterEntryRepositoryPort {

    private final DeadLetterEntryJdbcRepository deadLetterEntryJdbcRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void save(DeadLetterEntry entry) {
        long start = System.nanoTime();
        if (deadLetterEntryJdbcRepository.existsById(entry.getId())) {
            deadLetterEntryJdbcRepository.save(DeadLetterEntryEntityMapper.toEntity(entry));
        } else {
            jdbcTemplate.update(
                    """
                    INSERT INTO dead_letter_entries (
                        id,
                        portfolio_id,
                        runner_id,
                        client_order_id,
                        exchange_order_id,
                        raw_payload,
                        reason,
                        is_resolved,
                        resolved_by,
                        resolved_at,
                        created_at
                    ) VALUES (
                        :id,
                        :portfolioId,
                        :runnerId,
                        :clientOrderId,
                        :exchangeOrderId,
                        :rawPayload,
                        :reason,
                        :isResolved,
                        :resolvedBy,
                        :resolvedAt,
                        :createdAt
                    )
                    """,
                    new MapSqlParameterSource()
                            .addValue("id", entry.getId())
                            .addValue("portfolioId", entry.getPortfolioId())
                            .addValue("runnerId", entry.getRunnerId())
                            .addValue("clientOrderId", entry.getClientOrderId())
                            .addValue("exchangeOrderId", entry.getExchangeOrderId())
                            .addValue("rawPayload", entry.getRawPayload())
                            .addValue("reason", entry.getReason().name())
                            .addValue("isResolved", entry.isResolved())
                            .addValue("resolvedBy", entry.getResolvedBy())
                            .addValue("resolvedAt", entry.getResolvedAt() == null ? null : Timestamp.from(entry.getResolvedAt()))
                            .addValue("createdAt", Timestamp.from(entry.getCreatedAt()))
            );
        }
        log.trace("[REPO] deadLetterEntry.save({}) - {}ms", entry.getId(), RepoTiming.elapsedMs(start));
    }

    @Override
    public Optional<DeadLetterEntry> findById(UUID id) {
        long start = System.nanoTime();
        Optional<DeadLetterEntry> result = deadLetterEntryJdbcRepository.findById(id)
                .map(DeadLetterEntryEntityMapper::toDomain);
        log.trace("[REPO] deadLetterEntry.findById({}) - {}ms - found={}",
                id, RepoTiming.elapsedMs(start), result.isPresent());
        return result;
    }

    @Override
    public List<DeadLetterEntry> findUnresolved(UUID portfolioId, UUID runnerId, int limit) {
        long start = System.nanoTime();
        int boundedLimit = Math.max(1, limit);
        List<DeadLetterEntry> result = deadLetterEntryJdbcRepository
                .findUnresolved(portfolioId, runnerId, boundedLimit)
                .stream()
                .map(DeadLetterEntryEntityMapper::toDomain)
                .toList();
        log.trace("[REPO] deadLetterEntry.findUnresolved(portfolioId={}, runnerId={}, limit={}) - {}ms - {} results",
                portfolioId, runnerId, boundedLimit, RepoTiming.elapsedMs(start), result.size());
        return result;
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
