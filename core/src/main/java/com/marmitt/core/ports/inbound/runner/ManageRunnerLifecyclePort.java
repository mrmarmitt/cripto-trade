package com.marmitt.core.ports.inbound.runner;

import com.marmitt.core.dto.runner.request.UpdateRunnerLimitsRequest;
import com.marmitt.core.dto.runner.response.RunnerTransitionResult;

import java.util.UUID;

/**
 * Ciclo de vida do runner por comando do operador (IG §13.1, §13.6, T49).
 */
public interface ManageRunnerLifecyclePort {

    /** CREATED → ACTIVE, reconciliando o runner pelo mesmo caminho do boot. */
    RunnerTransitionResult start(UUID runnerId);

    /** HALTED → ACTIVE, só se a reconciliação terminar limpa (inclui DLQ resolvida). */
    RunnerTransitionResult resume(UUID runnerId);

    /**
     * ACTIVE/HALTED → TERMINATING: sem novas compras; a estratégia ainda vende. Arquivado quando
     * não houver posição nem ordem em voo.
     */
    RunnerTransitionResult terminate(UUID runnerId);

    /** Arquiva os runners TERMINATING que já não têm posição nem ordem em voo. */
    void archiveCompleted();

    RunnerTransitionResult updateLimits(UUID runnerId, UpdateRunnerLimitsRequest request);
}
