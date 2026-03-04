package com.marmitt.application.spring.infrastructure.persistence.adapter;

import com.marmitt.core.ports.outbound.repository.CapitalEventIdempotencyPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class JdbcCapitalEventIdempotencyRepositoryAdapter implements CapitalEventIdempotencyPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public boolean tryRegisterExecutionConfirmed(UUID matchId) {
        return tryInsert("EXECUTION_CONFIRMED", matchId);
    }

    @Override
    public boolean tryRegisterMarginRelease(UUID transactionId) {
        return tryInsert("MARGIN_RELEASE", transactionId);
    }

    private boolean tryInsert(String eventType, UUID eventId) {
        long start = System.nanoTime();
        String eventKey = eventType + ":" + eventId;
        try {
            int updated = jdbcTemplate.update(
            """
            INSERT INTO capital_event_ledger(event_key, event_type, event_id, created_at)
            VALUES (:eventKey, :eventType, :eventId, NOW())
            ON CONFLICT (event_key) DO NOTHING
            """,
                    new MapSqlParameterSource()
                            .addValue("eventKey", eventKey)
                            .addValue("eventType", eventType)
                            .addValue("eventId", eventId));

            boolean inserted = updated == 1;
            log.trace("[REPO] capitalEventLedger.tryInsert({}, {}) - {}ms - inserted={}",
                    eventType, eventId, RepoTiming.elapsedMs(start), inserted);
            return inserted;
        } catch (DataAccessException e) {
            log.error("[REPO] capitalEventLedger.tryInsert({}, {}) - {}ms - error={}",
                    eventType, eventId, RepoTiming.elapsedMs(start), e.getMessage(), e);
            throw e;
        }
    }
}
