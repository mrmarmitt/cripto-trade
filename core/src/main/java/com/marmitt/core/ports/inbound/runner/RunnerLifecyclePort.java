package com.marmitt.core.ports.inbound.runner;

import java.util.UUID;

/**
 * Porta de entrada para o ciclo de vida de um StrategyRunner.
 * <p>
 * Gerencia a ativação e desativação de Runners como listeners de market data
 * e callbacks de ordens. Implementada por {@code RunnerLifecycleUseCase}.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 6.1</a>
 */
public interface RunnerLifecyclePort {

    /**
     * Ativa um Runner: carrega o Runner e a Strategy associada, cria um {@code RunnerUseCase}
     * e o registra como {@code PriceUpdateListener} e {@code OrderUpdateListener}.
     *
     * @param runnerId ID do Runner a ativar
     * @throws IllegalStateException se o Runner ou a Strategy não forem encontrados
     */
    void activate(UUID runnerId);

    /**
     * Desativa um Runner: remove seus listeners e libera os recursos em memória.
     * Se o Runner não estiver ativo, a chamada é ignorada silenciosamente.
     *
     * @param runnerId ID do Runner a desativar
     */
    void deactivate(UUID runnerId);
}
