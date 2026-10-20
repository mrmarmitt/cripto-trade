package com.marmitt.core.ports.outbound.operations;

import com.marmitt.core.enums.BootRunStatus;

import java.util.List;

/**
 * Sinais tecnicos que so a camada de composicao conhece (T55): resultado do boot e circuit breakers das
 * consultas REST as exchanges. O core decide a severidade.
 */
public interface InfrastructureSignalsPort {

    BootState bootState();

    /**
     * Exchanges com o circuit breaker das consultas REST aberto (T24).
     */
    List<String> openRestCircuits();

    /**
     * @param detail fase e motivo da falha, quando houver
     */
    record BootState(BootRunStatus status, String detail) {

        public boolean completed() {
            return status == BootRunStatus.SUCCESS;
        }
    }
}
