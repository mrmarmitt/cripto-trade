package com.marmitt.application.spring.infrastructure.persistence.adapter;

import com.marmitt.core.ports.outbound.repository.EquitySnapshotRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class JdbcEquitySnapshotRepositoryAdapter implements EquitySnapshotRepositoryPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public void record(UUID portfolioId, BigDecimal equity, Instant sampledAt) {
        long start = System.nanoTime();
        jdbcTemplate.update("""
                INSERT INTO equity_snapshots(portfolio_id, sampled_at, equity)
                VALUES (:portfolioId, :sampledAt, :equity)
                ON CONFLICT (portfolio_id, sampled_at) DO NOTHING
                """,
                new MapSqlParameterSource()
                        .addValue("portfolioId", portfolioId)
                        .addValue("sampledAt", Timestamp.from(sampledAt))
                        .addValue("equity", equity));
        log.trace("[REPO] equitySnapshot.record({}) - {}ms", portfolioId, RepoTiming.elapsedMs(start));
    }

    @Override
    public Optional<BigDecimal> peakSince(UUID portfolioId, Instant since) {
        long start = System.nanoTime();
        BigDecimal peak = jdbcTemplate.queryForObject("""
                SELECT MAX(equity) FROM equity_snapshots
                 WHERE portfolio_id = :portfolioId AND sampled_at >= :since
                """,
                new MapSqlParameterSource()
                        .addValue("portfolioId", portfolioId)
                        .addValue("since", Timestamp.from(since)),
                BigDecimal.class);
        log.trace("[REPO] equitySnapshot.peakSince({}) - {}ms", portfolioId, RepoTiming.elapsedMs(start));
        return Optional.ofNullable(peak);
    }

    @Override
    public int deleteBefore(Instant cutoff) {
        return jdbcTemplate.update("DELETE FROM equity_snapshots WHERE sampled_at < :cutoff",
                new MapSqlParameterSource().addValue("cutoff", Timestamp.from(cutoff)));
    }
}
