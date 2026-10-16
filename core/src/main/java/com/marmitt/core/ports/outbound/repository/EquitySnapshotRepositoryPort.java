package com.marmitt.core.ports.outbound.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Amostras de equity por portfolio para o monitor de drawdown (T43). Persistidas para que o pico da
 * janela sobreviva a restart.
 */
public interface EquitySnapshotRepositoryPort {

    void record(UUID portfolioId, BigDecimal equity, Instant sampledAt);

    /** Maior equity amostrado do portfolio a partir de {@code since} (inclusive). */
    Optional<BigDecimal> peakSince(UUID portfolioId, Instant since);

    /** Remove amostras anteriores a {@code cutoff}; retorna quantas foram removidas. */
    int deleteBefore(Instant cutoff);
}
