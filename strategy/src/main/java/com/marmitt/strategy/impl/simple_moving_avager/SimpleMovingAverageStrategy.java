package com.marmitt.strategy.impl.simple_moving_avager;

import com.marmitt.core.ports.outbound.strategy.TradingStrategy;
import com.marmitt.core.dto.strategy.StrategyInputDto;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.dto.strategy.PortfolioContextDto;
import com.marmitt.core.dto.strategy.OpenLotDto;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
public class SimpleMovingAverageStrategy implements TradingStrategy {

    private static final String STRATEGY_NAME = "SimpleMovingAverageStrategy";
    private static final String STRATEGY_VERSION = "1.0.0";
    private static final UUID STRATEGY_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

    private boolean enabled = true;
    private final SimpleMovingAverageConfig config;
    private final List<BigDecimal> priceHistory = new ArrayList<>();

    public SimpleMovingAverageStrategy() {
        this(SimpleMovingAverageConfig.defaultConfig());
    }

    public SimpleMovingAverageStrategy(SimpleMovingAverageConfig config) {
        this.config = config;
    }

    @Override
    public UUID getStrategyId() {
        return STRATEGY_ID;
    }

    @Override
    public String getStrategyName() {
        return STRATEGY_NAME;
    }

    @Override
    public String getStrategyVersion() {
        return STRATEGY_VERSION;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public StrategyOutputDto executeStrategy(StrategyInputDto input, PortfolioContextDto portfolioContext) {
        BigDecimal currentPrice = input.currentPrice();
        updateHistory(currentPrice);

        // 1. Aguardar historico minimo
        if (priceHistory.size() < config.movingAveragePeriod()) {
            log.debug("SMA: waiting history {}/{} currentPrice={}",
                    priceHistory.size(), config.movingAveragePeriod(), currentPrice);
            return StrategyOutputDto.hold(STRATEGY_NAME,
                    "Aguardando historico: " + priceHistory.size() + "/" + config.movingAveragePeriod());
        }

        // 2. Verificar lotes abertos - saida por lucro ou timeout
        if (portfolioContext.hasOpenLots()) {
            for (OpenLotDto lot : portfolioContext.openLots()) {
                BigDecimal lotProfit = portfolioContext.calculateLotProfit(lot.lotId(), currentPrice)
                        .orElse(BigDecimal.ZERO);

                long minutesActive = Duration.between(lot.openedAt(), Instant.now()).toMinutes();
                boolean isExpired = config.positionTimeoutMinutes() > 0
                        && minutesActive >= config.positionTimeoutMinutes();

                // sellThreshold esta em escala decimal (ex: 0.02 = 2%), lotProfit em % (ex: 2.00)
                boolean isProfitable = lotProfit.compareTo(
                        config.sellThreshold().multiply(BigDecimal.valueOf(100))) >= 0;

                if (isProfitable || isExpired) {
                    String reason = isExpired
                            ? "Timeout (" + minutesActive + "min)"
                            : "Take Profit (" + lotProfit.setScale(2, RoundingMode.HALF_UP) + "%)";

                    return StrategyOutputDto.sellLot(
                            STRATEGY_NAME,
                            BigDecimal.ONE,
                            lot.availableQuantity(),
                            lot.lotId(),
                            reason + " para lote " + lot.lotId()
                    );
                }
            }
        }

        // 3. Sinal de compra - cruzamento de SMA para cima
        BigDecimal sma = calculateSMA();
        BigDecimal previousPrice = resolvePreviousPrice(input);
        log.debug("SMA: current={} previous={} sma={} size={}",
                currentPrice, previousPrice, sma, priceHistory.size());
        if (previousPrice != null
                && currentPrice.compareTo(sma) > 0
                && previousPrice.compareTo(sma) <= 0) {
            BigDecimal buyQuantity = calculateBuyQuantity(currentPrice, portfolioContext);
            if (buyQuantity.compareTo(BigDecimal.ZERO) > 0) {
                return StrategyOutputDto.buy(STRATEGY_NAME, new BigDecimal("0.8"), buyQuantity,
                        "Cruzamento de SMA (Alta)");
            }
        }

        return StrategyOutputDto.hold(STRATEGY_NAME, "Nenhum sinal de lucro ou cruzamento de media.");
    }

    private void updateHistory(BigDecimal price) {
        priceHistory.add(price);
        while (priceHistory.size() > config.movingAveragePeriod()) {
            priceHistory.removeFirst();
        }
    }

    private BigDecimal resolvePreviousPrice(StrategyInputDto input) {
        if (input.previousPrice() != null) {
            return input.previousPrice();
        }
        if (priceHistory.size() < 2) {
            return null;
        }
        return priceHistory.get(priceHistory.size() - 2);
    }

    private BigDecimal calculateSMA() {
        BigDecimal sum = priceHistory.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(priceHistory.size()), 8, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateBuyQuantity(BigDecimal currentPrice, PortfolioContextDto portfolioContext) {
        if (!portfolioContext.hasMinimumBalance()) {
            log.debug("SMA: insufficient available balance for min operation - available={} minRequired={}",
                    portfolioContext.availableBalance(), portfolioContext.minimumOperationAmount());
            return BigDecimal.ZERO;
        }

        BigDecimal totalCapital = portfolioContext.totalCapital();
        BigDecimal allocationValue = totalCapital.multiply(config.allocationPercentage());

        BigDecimal availableBalance = portfolioContext.availableBalance();
        BigDecimal operationValue = allocationValue.min(availableBalance);

        if (operationValue.compareTo(portfolioContext.minimumOperationAmount()) < 0) {
            log.debug("SMA: operation below minimum - operationValue={} minRequired={}",
                    operationValue, portfolioContext.minimumOperationAmount());
            return BigDecimal.ZERO;
        }

        return operationValue.divide(currentPrice, 8, RoundingMode.DOWN);
    }
}
