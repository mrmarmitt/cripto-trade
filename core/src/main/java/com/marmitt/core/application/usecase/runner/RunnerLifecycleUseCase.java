package com.marmitt.core.application.usecase.runner;

import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.ports.inbound.runner.HandleOrderTerminationPort;
import com.marmitt.core.ports.inbound.runner.ProcessTradeSignalPort;
import com.marmitt.core.ports.inbound.runner.RunnerLifecyclePort;
import com.marmitt.core.ports.outbound.repository.ListenerRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import com.marmitt.core.ports.outbound.strategy.TradingStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerencia o ciclo de vida de Runners ativos — cria, registra e remove instâncias de
 * {@link RunnerUseCase} como listeners de market data e callbacks de ordens.
 * <p>
 * Implementa {@link RunnerLifecyclePort}. Mantém um mapa em memória dos Runners ativos
 * para suporte ao {@link #deactivate}.
 * <p>
 * <b>activate</b>: carrega o Runner e a Strategy associada, cria um {@link RunnerUseCase}
 * e o registra nos repositórios de listeners.
 * <p>
 * <b>deactivate</b>: remove os listeners e libera a instância. Se o Runner não estiver
 * ativo, a chamada é ignorada silenciosamente.
 *
 * @see RunnerUseCase
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 6.4</a>
 */
@Slf4j
public class RunnerLifecycleUseCase implements RunnerLifecyclePort {

    private final StrategyRunnerRepositoryPort runnerRepository;
    private final StrategyRepositoryPort strategyRepository;
    private final ListenerRepositoryPort listenerRepository;
    private final PortfolioContextBuilder contextBuilder;
    private final ProcessTradeSignalPort processTradeSignalPort;
    private final HandleOrderTerminationPort terminationPort;

    private final ConcurrentHashMap<UUID, RunnerUseCase> activeRunners = new ConcurrentHashMap<>();

    public RunnerLifecycleUseCase(
            StrategyRunnerRepositoryPort runnerRepository,
            StrategyRepositoryPort strategyRepository,
            ListenerRepositoryPort listenerRepository,
            PortfolioContextBuilder contextBuilder,
            ProcessTradeSignalPort processTradeSignalPort,
            HandleOrderTerminationPort terminationPort
    ) {
        this.runnerRepository = runnerRepository;
        this.strategyRepository = strategyRepository;
        this.listenerRepository = listenerRepository;
        this.contextBuilder = contextBuilder;
        this.processTradeSignalPort = processTradeSignalPort;
        this.terminationPort = terminationPort;
    }

    /**
     * Ativa um Runner: carrega Runner e Strategy, cria um {@link RunnerUseCase}
     * e o registra como {@code PriceUpdateListener} e {@code OrderUpdateListener}.
     *
     * @param runnerId ID do Runner a ativar
     * @throws IllegalStateException se o Runner ou a Strategy não forem encontrados
     */
    @Override
    public void activate(UUID runnerId) {
        StrategyRunner runner = runnerRepository.findById(runnerId)
                .orElseThrow(() -> new IllegalStateException("Runner not found: " + runnerId));

        TradingStrategy strategy = strategyRepository.findById(runner.getStrategyId())
                .orElseThrow(() -> new IllegalStateException(
                        "Strategy not found for runner=" + runnerId + " strategyId=" + runner.getStrategyId()));

        RunnerUseCase useCase = new RunnerUseCase(
                runner, strategy, contextBuilder, processTradeSignalPort, terminationPort, runnerRepository);

        listenerRepository.addPriceUpdateListener(useCase);
        listenerRepository.addOrderUpdateListener(useCase);
        activeRunners.put(runnerId, useCase);

        log.info("runnerLifecycle: activated runnerId={} symbol={} strategy={}",
                runnerId, runner.getSymbol(), runner.getStrategyName());
    }

    /**
     * Desativa um Runner: remove seus listeners e libera os recursos em memória.
     * Se o Runner não estiver ativo, a chamada é ignorada silenciosamente.
     *
     * @param runnerId ID do Runner a desativar
     */
    @Override
    public void deactivate(UUID runnerId) {
        RunnerUseCase useCase = activeRunners.remove(runnerId);
        if (useCase == null) {
            log.debug("runnerLifecycle: deactivate called on inactive runner runnerId={}", runnerId);
            return;
        }

        listenerRepository.removePriceUpdateListener(useCase);
        listenerRepository.removeOrderUpdateListener(useCase);

        log.info("runnerLifecycle: deactivated runnerId={}", runnerId);
    }
}
