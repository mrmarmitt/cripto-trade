package com.marmitt.application.spring.infrastructure.persistence.adapter;

import com.marmitt.core.domain.runner.PositionProtection;
import com.marmitt.core.dto.operations.TimelineEvent;
import com.marmitt.core.enums.TimelineEventType;
import com.marmitt.core.ports.outbound.repository.EventTimelineRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Linha do tempo (T59): uniao ordenada das tabelas que ja registram cada evento. Cada ramo filtra o
 * intervalo pelo proprio indice de instante; os tipos sem portfolio (comando do operador, catalogo, boot)
 * saem quando o filtro de portfolio e usado.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class JdbcEventTimelineRepositoryAdapter implements EventTimelineRepositoryPort {

    private static final String SQL = """
            SELECT * FROM (
                SELECT c.id::text AS id, 'SAFE_MODE_CHANGED' AS type, c.changed_at AS occurred_at,
                       c.portfolio_id, NULL::uuid AS runner_id, NULL::uuid AS transaction_id, NULL::uuid AS strategy_ref,
                       c.origin::text AS origin, NULL::text AS actor, c.from_status::text AS from_state,
                       c.to_status::text AS to_state, c.reason::text AS reason, NULL::text AS detail
                  FROM safe_mode_changes c
                 WHERE c.changed_at >= :since AND c.changed_at < :until
                   AND (CAST(:portfolioId AS uuid) IS NULL OR c.portfolio_id = CAST(:portfolioId AS uuid))
                UNION ALL
                SELECT c.id::text, 'RUNNER_STATUS_CHANGED', c.changed_at,
                       r.portfolio_id, c.runner_id, NULL::uuid, NULL::uuid,
                       c.origin, NULL::text, c.from_status, c.to_status, c.reason, NULL::text
                  FROM runner_status_changes c
                  JOIN strategy_runners r ON r.id = c.runner_id
                 WHERE c.changed_at >= :since AND c.changed_at < :until
                   AND (CAST(:portfolioId AS uuid) IS NULL OR r.portfolio_id = CAST(:portfolioId AS uuid))
                UNION ALL
                SELECT t.id::text, 'PROTECTIVE_EXIT', t.requested_at,
                       r.portfolio_id, t.runner_id, t.id, NULL::uuid,
                       'AUTOMATIC', NULL::text, NULL::text, NULL::text, t.reasoning,
                       'status=' || t.status || ' symbol=' || t.symbol || ' quantity=' || t.quantity
                  FROM transactions t
                  JOIN strategy_runners r ON r.id = t.runner_id
                 WHERE t.requested_at >= :since AND t.requested_at < :until
                   AND t.type = 'SELL' AND t.reasoning LIKE :protectiveExit
                   AND (CAST(:portfolioId AS uuid) IS NULL OR r.portfolio_id = CAST(:portfolioId AS uuid))
                UNION ALL
                SELECT d.id::text, 'DLQ_ENTRY', d.created_at,
                       d.portfolio_id, d.runner_id, NULL::uuid, NULL::uuid,
                       'AUTOMATIC', NULL::text, NULL::text, NULL::text, d.reason,
                       'clientOrderId=' || COALESCE(d.client_order_id, '-') || ' resolved=' || d.is_resolved
                  FROM dead_letter_entries d
                 WHERE d.created_at >= :since AND d.created_at < :until
                   AND (CAST(:portfolioId AS uuid) IS NULL OR d.portfolio_id = CAST(:portfolioId AS uuid))
                UNION ALL
                SELECT a.id::text, 'OPERATOR_ACTION', a.occurred_at,
                       NULL::uuid, NULL::uuid, NULL::uuid, NULL::uuid,
                       'MANUAL', a.actor, NULL::text, NULL::text, a.reason,
                       a.method || ' ' || a.path || ' -> ' || a.http_status || COALESCE(' ' || a.error_code, '')
                  FROM operator_actions a
                 WHERE a.occurred_at >= :since AND a.occurred_at < :until
                   AND CAST(:portfolioId AS uuid) IS NULL
                UNION ALL
                SELECT e.id::text, 'STRATEGY_CATALOG_CHANGED', e.occurred_at,
                       NULL::uuid, NULL::uuid, NULL::uuid, e.strategy_ref,
                       NULL::text, NULL::text, NULL::text, NULL::text, e.reason,
                       e.type || COALESCE(': ' || e.detail, '')
                  FROM remote_strategy_events e
                 WHERE e.occurred_at >= :since AND e.occurred_at < :until
                   AND CAST(:portfolioId AS uuid) IS NULL
                UNION ALL
                SELECT b.run_id, CASE b.status WHEN 'SUCCESS' THEN 'BOOT_COMPLETED' ELSE 'BOOT_FAILED' END,
                       b.finished_at,
                       NULL::uuid, NULL::uuid, NULL::uuid, NULL::uuid,
                       'BOOT', NULL::text, NULL::text, NULL::text, b.failure_message,
                       'mode=' || b.mode || COALESCE(' failurePhase=' || b.failure_phase, '')
                           || ' durationMs=' || (EXTRACT(EPOCH FROM (b.finished_at - b.started_at)) * 1000)::bigint
                  FROM boot_runs b
                 WHERE b.finished_at >= :since AND b.finished_at < :until
                   AND CAST(:portfolioId AS uuid) IS NULL
            ) events
            ORDER BY occurred_at, id
            LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public List<TimelineEvent> find(Instant since, Instant until, UUID portfolioId, int limit) {
        long start = System.nanoTime();
        List<TimelineEvent> events = jdbcTemplate.query(SQL,
                new MapSqlParameterSource()
                        .addValue("since", Timestamp.from(since))
                        .addValue("until", Timestamp.from(until))
                        .addValue("portfolioId", portfolioId != null ? portfolioId.toString() : null, Types.VARCHAR)
                        .addValue("protectiveExit", PositionProtection.EXIT_REASONING_PREFIX + "%")
                        .addValue("limit", limit),
                (rs, rowNum) -> toEvent(rs));
        log.trace("[REPO] eventTimeline.find({}, {}, {}) - {}ms - {} results", since, until, portfolioId,
                RepoTiming.elapsedMs(start), events.size());
        return events;
    }

    private static TimelineEvent toEvent(ResultSet rs) throws SQLException {
        return new TimelineEvent(
                rs.getString("id"),
                TimelineEventType.valueOf(rs.getString("type")),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getObject("portfolio_id", UUID.class),
                rs.getObject("runner_id", UUID.class),
                rs.getObject("transaction_id", UUID.class),
                rs.getObject("strategy_ref", UUID.class),
                rs.getString("origin"),
                rs.getString("actor"),
                rs.getString("from_state"),
                rs.getString("to_state"),
                rs.getString("reason"),
                rs.getString("detail"));
    }
}
