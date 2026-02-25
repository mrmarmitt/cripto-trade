package com.marmitt.core.application.listener.runner;

import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.websocket.data.MarketDataDto;
import com.marmitt.core.ports.inbound.runner.ProcessTradeSignalPort;
import com.marmitt.core.ports.outbound.listener.PriceUpdateListener;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

/**
 * Listener responsável por rotear atualizações de preço para os {@link StrategyRunner}s elegíveis.
 * <p>
 * Para cada mensagem de market data recebida:
 * <ol>
 *   <li>Consulta Runners operacionais pelo símbolo e exchange (JOIN com Portfolio.isActive)</li>
 *   <li>Filtra em memória por {@link StrategyRunner#canAcceptSignals()} e
 *       {@link StrategyRunner#canReceiveMarketDataFrom(String)}</li>
 *   <li>Executa a estratégia com o contexto atual do Runner</li>
 *   <li>Encaminha o sinal (BUY/SELL) para {@link ProcessTradeSignalPort}</li>
 * </ol>
 * <p>
 * Um único listener que roteia internamente, sem caching — consulta o banco a cada mensagem.
 *
 * @see StrategyRunnerRepositoryPort#findOperationalBySymbol(String, String)
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 6.4</a>
 */
@Slf4j
public class PortfolioStrategyRunnerPriceUpdateListener implements PriceUpdateListener {

    private final ProcessTradeSignalPort processTradeSignal;

    public PortfolioStrategyRunnerPriceUpdateListener(ProcessTradeSignalPort processTradeSignal) {
        this.processTradeSignal = processTradeSignal;
    }

    @Override
    public void onPriceUpdate(MarketDataDto marketData) {
        processTradeSignal.execute(marketData);
    }
}
