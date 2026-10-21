package com.marmitt.core.ports.outbound.repository;

import com.marmitt.core.dto.boot.BootRun;

/**
 * Historico das execucoes do boot (T59). O estado do boot corrente continua em memoria
 * ({@code /api/boot/status}); aqui fica o que sobrevive ao restart.
 */
public interface BootRunRepositoryPort {

    void save(BootRun run);
}
