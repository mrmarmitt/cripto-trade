package com.marmitt.core.application.usecase.portfolio;

import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.ports.outbound.marketdata.LatestPricePort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PnL flutuante do portfolio ao ultimo preco conhecido (T43). Calculo unico para o monitor de
 * drawdown e para a consulta de PnL: posicoes ativas (OPEN e CLOSING — lote travado por venda em voo
 * ainda e do portfolio), cada uma marcada pelo {@link LatestPricePort} e somada pelo
 * {@link Position#getUnrealizedPnl()}.
 */
class PortfolioMarkToMarket {

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final LatestPricePort latestPrices;

    PortfolioMarkToMarket(StrategyRunnerRepositoryPort strategyRunnerRepository, LatestPricePort latestPrices) {
        this.strategyRunnerRepository = strategyRunnerRepository;
        this.latestPrices = latestPrices;
    }

    Valuation value(UUID portfolioId) {
        BigDecimal unrealized = BigDecimal.ZERO;
        List<Position> unpriced = new ArrayList<>();
        Instant oldestPriceAt = null;
        for (Position position : strategyRunnerRepository.findActivePositionsByPortfolioId(portfolioId)) {
            var price = latestPrices.find(position.getSymbol());
            if (price.isEmpty()) {
                unpriced.add(position);
                continue;
            }
            position.updateCurrentPrice(price.get());
            unrealized = unrealized.add(position.getUnrealizedPnl());
            Instant priceAt = latestPrices.findUpdatedAt(position.getSymbol()).orElse(null);
            if (priceAt != null && (oldestPriceAt == null || priceAt.isBefore(oldestPriceAt))) {
                oldestPriceAt = priceAt;
            }
        }
        return new Valuation(unrealized, List.copyOf(unpriced), oldestPriceAt);
    }

    /**
     * @param unrealizedPnl soma do flutuante das posicoes com preco conhecido
     * @param unpriced      posicoes ainda sem preco (ex.: logo apos restart, antes do primeiro tick)
     * @param oldestPriceAt instante do preco mais antigo usado; nulo sem posicao com preco (T58)
     */
    record Valuation(BigDecimal unrealizedPnl, List<Position> unpriced, Instant oldestPriceAt) {
        boolean complete() {
            return unpriced.isEmpty();
        }
    }
}
