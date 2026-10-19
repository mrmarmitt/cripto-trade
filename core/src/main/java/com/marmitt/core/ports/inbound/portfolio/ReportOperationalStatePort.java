package com.marmitt.core.ports.inbound.portfolio;

import com.marmitt.core.enums.SafeModeStatus;

import java.util.Map;
import java.util.UUID;

/**
 * Exposição do estado operacional (T51): amostra periódica para as métricas e leitura do nível do
 * Safe Mode para o health.
 */
public interface ReportOperationalStatePort {

    /** Amostra portfolios e runners e publica o estado em {@code OperationalMetricsPort}. */
    void report();

    /** Nível atual do Safe Mode de cada portfolio. */
    Map<UUID, SafeModeStatus> safeModeByPortfolio();
}
