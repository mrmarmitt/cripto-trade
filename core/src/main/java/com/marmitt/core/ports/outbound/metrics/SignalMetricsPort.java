package com.marmitt.core.ports.outbound.metrics;

import java.util.UUID;

/**
 * Porta outbound para emissao de metricas de avaliacao de sinal (T23 G2).
 *
 * <p>Mantem o core livre de Micrometer: a camada de composicao (spring-application)
 * fornece a implementacao concreta. Espelha o padrao de
 * {@link com.marmitt.core.ports.outbound.boot.BootExecutionObserverPort}.
 */
public interface SignalMetricsPort {

    /**
     * Registra que um tick foi avaliado para um runner, com o desfecho {@code decision}.
     *
     * @param runnerId runner avaliado (vira tag {@code runnerId})
     * @param decision desfecho da avaliacao (vira tag {@code decision})
     */
    void recordSignalEvaluated(UUID runnerId, SignalDecision decision);
}
