package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.enums.ExecutionPolicy;
import com.marmitt.core.enums.TradingAction;
import lombok.extern.slf4j.Slf4j;

/**
 * Guardas de elegibilidade que determinam se um runner pode receber e executar sinais.
 *
 * <p>Existem dois pontos de verificacao distintos no pipeline, com propositos diferentes:
 * <ul>
 *   <li>{@link #canProcessRunner}: filtro <em>estrutural</em> aplicado antes de qualquer
 *       consulta ao banco ou execucao de estrategia. Descarta rapidamente runners que nao
 *       estao aptos a receber market data (status invalido, exchange nao autorizada).</li>
 *   <li>{@link #canExecuteSignal}: guarda de <em>negocio</em> aplicado depois que a
 *       estrategia ja produziu um sinal. Avalia regras que dependem do sinal em si, como
 *       a politica de execucao SINGLE que proibe novo BUY quando ha posicao aberta.</li>
 * </ul>
 *
 * <p>A separacao evita carregar snapshot de exposicao ({@link RunnerExposureService})
 * para runners que seriam descartados antes mesmo de precisar desse dado.
 */
@Slf4j
class RunnerSignalPolicy {

    /**
     * Filtro estrutural rapido: verifica se o runner esta apto a receber ticks
     * antes de qualquer consulta ao banco ou execucao de estrategia.
     *
     * <p>Rejeita o runner se:
     * <ul>
     *   <li>o status do runner nao permite aceitar sinais (ex: PAUSED, TERMINATING); ou</li>
     *   <li>a exchange de origem do tick nao esta na lista de fontes autorizadas do runner.</li>
     * </ul>
     */
    public boolean canProcessRunner(StrategyRunner runner, String exchangeId) {
        if (!runner.canAcceptSignals()) {
            log.debug("priceUpdate: skipping runner={} - canAcceptSignals=false (status={} reconciling={})",
                    runner.getId(), runner.getStatus(), runner.isReconciling());
            return false;
        }

        if (!runner.canReceiveMarketDataFrom(exchangeId)) {
            log.debug("priceUpdate: skipping runner={} - exchange={} not in allowedSources",
                    runner.getId(), exchangeId);
            return false;
        }

        return true;
    }

    /**
     * Guarda de negocio: verifica se o sinal produzido pela estrategia pode ser
     * materializado em uma ordem, dado o estado atual do runner.
     *
     * <p>Rejeita o sinal se:
     * <ul>
     *   <li>o runner perdeu elegibilidade entre o inicio do tick e agora (verificacao
     *       defensiva — {@code canProcessRunner} ja fez esta checagem, mas o estado
     *       pode ter mudado durante a avaliacao da estrategia);</li>
     *   <li>o sinal e HOLD;</li>
     *   <li>a politica e {@code SINGLE}, o sinal e BUY e ja existe posicao aberta
     *       ou ordem em voo — o runner nao pode ter mais de uma posicao simultanea.</li>
     * </ul>
     *
     * @param hasOpenPositionOrInFlight pre-calculado por {@link RunnerExposureService#loadSnapshot};
     *                                  sera {@code false} se a checagem nao foi necessaria
     *                                  (runner nao usa politica SINGLE ou sinal nao e BUY)
     */
    public boolean canExecuteSignal(StrategyRunner runner,
                                    StrategyOutputDto signal,
                                    boolean hasOpenPositionOrInFlight) {
        if (!runner.canAcceptSignals()) {
            log.warn("processTradeSignal: runner={} cannot accept signals (status={} reconciling={})",
                    runner.getId(), runner.getStatus(), runner.isReconciling());
            return false;
        }

        if (signal.decision() == TradingAction.SHOULD_HOLD) {
            log.debug("processTradeSignal: HOLD signal discarded for runner={}", runner.getId());
            return false;
        }

        if (runner.getExecutionPolicy() == ExecutionPolicy.SINGLE
                && signal.decision() == TradingAction.SHOULD_BUY
                && hasOpenPositionOrInFlight) {
            log.info("processTradeSignal: BUY signal rejected by SINGLE policy - " +
                            "open position or in-flight orders exist for runner={}",
                    runner.getId());
            return false;
        }

        return true;
    }

    /**
     * Indica se o sinal exige carregamento do snapshot de exposicao antes da execucao.
     *
     * <p>Apenas runners com politica {@code SINGLE} e sinal BUY precisam saber se ja
     * existe posicao aberta ou ordem em voo — e a unica regra que rejeita BUY com base
     * nessa informacao. Carregar o snapshot desnecessariamente adicionaria uma consulta
     * ao banco em todos os ticks de runners MULTIPLE ou em sinais SELL.
     */
    public boolean requiresOpenPositionCheck(StrategyRunner runner, StrategyOutputDto signal) {
        return runner.getExecutionPolicy() == ExecutionPolicy.SINGLE
                && signal.decision() == TradingAction.SHOULD_BUY;
    }
}
