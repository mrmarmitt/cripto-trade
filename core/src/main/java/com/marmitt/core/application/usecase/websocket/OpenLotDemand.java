package com.marmitt.core.application.usecase.websocket;

import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runners nao arquivados que tem lote aberto ({@code OPEN}/{@code CLOSING}) (T58). O lote precisa de preco
 * mesmo com o runner parado: o drawdown (T43) e o PnL flutuante marcam o lote a mercado. Usado pelo
 * coordenador de market data (demanda) e pelo estado consolidado ({@code MARKET_DATA_STALE}), para os dois
 * enxergarem a mesma demanda.
 */
public class OpenLotDemand {

    private final StrategyRunnerRepositoryPort runnerRepository;

    public OpenLotDemand(StrategyRunnerRepositoryPort runnerRepository) {
        this.runnerRepository = runnerRepository;
    }

    /** Dentre {@code runners}, os nao arquivados com pelo menos um lote aberto, na ordem recebida. */
    public List<StrategyRunner> runnersWithOpenLots(List<StrategyRunner> runners) {
        Map<UUID, StrategyRunner> candidates = new LinkedHashMap<>();
        runners.stream()
                .filter(runner -> runner.getStatus() != RunnerStatus.ARCHIVED)
                .forEach(runner -> candidates.put(runner.getId(), runner));
        Map<UUID, StrategyRunner> withLots = new LinkedHashMap<>();
        candidates.values().stream().map(StrategyRunner::getPortfolioId).distinct().forEach(portfolioId -> {
            for (Position lot : runnerRepository.findActivePositionsByPortfolioId(portfolioId)) {
                StrategyRunner owner = candidates.get(lot.getRunnerId());
                if (owner != null) {
                    withLots.putIfAbsent(owner.getId(), owner);
                }
            }
        });
        return candidates.values().stream().filter(runner -> withLots.containsKey(runner.getId())).toList();
    }
}
