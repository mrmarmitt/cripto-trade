package com.marmitt.core.ports.outbound.metrics;

import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.enums.SafeModeStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Métricas de estado operacional (T51; IG §7.7, §11.7, §13.7): Safe Mode, utilização de capital e
 * ciclo de vida dos runners. Só exposição de estado — nenhuma regra decide com base nelas.
 */
public interface OperationalMetricsPort {

    /**
     * Estado do portfolio na última amostra.
     *
     * @param safeModeActiveFor tempo desde a última mudança de nível, zero em NORMAL
     * @param globalUtilization reservado ÷ (disponível + reservado), fração
     * @param haltedRunners     runners em HALTED do portfolio
     */
    void recordPortfolioState(UUID portfolioId, SafeModeStatus level, Duration safeModeActiveFor,
                              BigDecimal globalUtilization, long haltedRunners);

    /**
     * Estado de um runner operacional na última amostra.
     *
     * @param capitalUtilization exposição em voo ÷ limite de alocação (mesma conta da reserva), fração
     * @param oldestInFlightAge  idade da ordem em voo mais antiga (reserva mais longa), zero sem ordem
     */
    void recordRunnerState(UUID runnerId, BigDecimal capitalUtilization, Duration oldestInFlightAge);

    /**
     * Contagem global de runners por status e dos que estão em TERMINATING além do timeout.
     */
    void recordRunnerCounts(Map<RunnerStatus, Long> runnersByStatus, long terminationStuck);

    /** Transição de status de um runner ({@code from} nulo na criação). */
    void recordTransition(RunnerStatus from, RunnerStatus to);

    /** Tempo que o runner passou em TERMINATING até ser arquivado. */
    void recordTerminatingDuration(Duration duration);
}
