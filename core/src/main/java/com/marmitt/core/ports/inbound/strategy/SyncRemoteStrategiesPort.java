package com.marmitt.core.ports.inbound.strategy;

/**
 * Alinha as estrategias em uso com o catalogo (T54): coloca no repositorio as que ainda nao estao e
 * confere as habilitadas que ainda nao foram conferidas neste processo. Idempotente; chamado no boot e
 * periodicamente, para pegar servicos que subiram depois do ctrade.
 */
public interface SyncRemoteStrategiesPort {

    void syncAll();
}
