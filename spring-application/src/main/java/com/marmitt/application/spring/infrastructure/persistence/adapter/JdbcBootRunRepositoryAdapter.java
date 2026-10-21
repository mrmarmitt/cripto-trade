package com.marmitt.application.spring.infrastructure.persistence.adapter;

import com.marmitt.core.dto.boot.BootRun;
import com.marmitt.core.ports.outbound.repository.BootRunRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

@Slf4j
@Repository
@RequiredArgsConstructor
public class JdbcBootRunRepositoryAdapter implements BootRunRepositoryPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public void save(BootRun run) {
        long start = System.nanoTime();
        jdbcTemplate.update("""
                INSERT INTO boot_runs(run_id, mode, status, started_at, finished_at, failure_phase, failure_message)
                VALUES (:runId, :mode, :status, :startedAt, :finishedAt, :failurePhase, :failureMessage)
                """,
                new MapSqlParameterSource()
                        .addValue("runId", run.runId())
                        .addValue("mode", run.mode())
                        .addValue("status", run.status().name())
                        .addValue("startedAt", Timestamp.from(run.startedAt()))
                        .addValue("finishedAt", Timestamp.from(run.finishedAt()))
                        .addValue("failurePhase", run.failurePhase())
                        .addValue("failureMessage", run.failureMessage()));
        log.trace("[REPO] bootRun.save({}) - {}ms", run.runId(), RepoTiming.elapsedMs(start));
    }
}
