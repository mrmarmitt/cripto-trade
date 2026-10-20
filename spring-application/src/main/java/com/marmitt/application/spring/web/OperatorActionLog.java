package com.marmitt.application.spring.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Log unico das acoes do operador pela API (T55): cada comando que muda estado, com quem pediu, o
 * motivo e o resultado (status HTTP e codigo de recusa). Auditoria tecnica da borda HTTP — as regras de
 * negocio de cada comando continuam no core.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperatorActionLog {

    private static final RowMapper<OperatorAction> ROW = (rs, rowNum) -> new OperatorAction(
            rs.getObject("id", UUID.class),
            rs.getString("actor"),
            rs.getString("method"),
            rs.getString("path"),
            rs.getString("operation"),
            rs.getString("reason"),
            rs.getInt("http_status"),
            rs.getString("error_code"),
            rs.getLong("duration_ms"),
            rs.getTimestamp("occurred_at").toInstant());

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * Grava a acao. Falha de gravacao e logada e nao propaga: a auditoria nao pode desfazer nem mascarar
     * o resultado de um comando ja executado.
     */
    public void record(OperatorAction action) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO operator_actions (id, actor, method, path, operation, reason, http_status,
                                                  error_code, duration_ms, occurred_at)
                    VALUES (:id, :actor, :method, :path, :operation, :reason, :httpStatus, :errorCode,
                            :durationMs, :occurredAt)
                    """,
                    new MapSqlParameterSource()
                            .addValue("id", action.id())
                            .addValue("actor", action.actor())
                            .addValue("method", action.method())
                            .addValue("path", action.path())
                            .addValue("operation", action.operation())
                            .addValue("reason", action.reason())
                            .addValue("httpStatus", action.httpStatus())
                            .addValue("errorCode", action.errorCode())
                            .addValue("durationMs", action.durationMs())
                            .addValue("occurredAt", Timestamp.from(action.occurredAt())));
        } catch (RuntimeException e) {
            log.error("operatorAction: audit write failed {} {} status={} - {}", action.method(), action.path(),
                    action.httpStatus(), e.getMessage(), e);
        }
    }

    /**
     * Acoes desde {@code since}, da mais recente para a mais antiga.
     *
     * @param pathContains filtro opcional por trecho do caminho (ex.: o id de um runner)
     */
    public List<OperatorAction> find(Instant since, String pathContains, int limit) {
        return jdbcTemplate.query("""
                SELECT * FROM operator_actions
                 WHERE occurred_at >= :since
                   AND (CAST(:pathContains AS TEXT) IS NULL OR path LIKE '%' || :pathContains || '%')
                 ORDER BY seq DESC
                 LIMIT :limit
                """,
                new MapSqlParameterSource()
                        .addValue("since", Timestamp.from(since))
                        .addValue("pathContains", pathContains == null || pathContains.isBlank() ? null : pathContains)
                        .addValue("limit", limit),
                ROW);
    }

    /**
     * @param operation metodo do controller que atendeu (ex.: {@code AdminController.haltById}); nulo se a
     *                  requisicao foi recusada antes de chegar a um controller
     * @param errorCode codigo da recusa ({@code ApiError.code}); nulo em sucesso
     */
    public record OperatorAction(UUID id, String actor, String method, String path, String operation,
                                 String reason, int httpStatus, String errorCode, long durationMs,
                                 Instant occurredAt) {
    }
}
