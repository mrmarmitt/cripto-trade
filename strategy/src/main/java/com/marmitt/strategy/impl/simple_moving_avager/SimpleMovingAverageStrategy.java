package com.marmitt.strategy.impl.simple_moving_avager;

import com.marmitt.core.ports.outbound.strategy.TradingStrategy;
import com.marmitt.core.dto.strategy.StrategyInputDto;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.dto.strategy.PortfolioContextDto;

import com.marmitt.core.dto.strategy.OpenBuyEntryDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SimpleMovingAverageStrategy implements TradingStrategy {

    private static final String STRATEGY_NAME = "SimpleMovingAverageStrategy";
    private static final String STRATEGY_VERSION = "1.0.0";
    // UUID fixo para garantir consistência entre restarts
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
    public StrategyOutputDto executeStrategy(StrategyInputDto inputData, PortfolioContextDto portfolioContext) {
        if (!enabled) {
            return StrategyOutputDto.hold(STRATEGY_NAME, "Strategy is disabled");
        }
        
        if (inputData.currentPrice() == null) {
            return StrategyOutputDto.hold(STRATEGY_NAME, "No current price available");
        }
        
        // Adiciona o preço atual ao histórico
        priceHistory.add(inputData.currentPrice());
        
        // Mantém apenas os últimos N preços
        if (priceHistory.size() > config.movingAveragePeriod()) {
            priceHistory.removeFirst();
        }
        
        // Precisa de pelo menos o período completo para calcular
        if (priceHistory.size() < config.movingAveragePeriod()) {
            return StrategyOutputDto.hold(STRATEGY_NAME,
                String.format("Insufficient data: %d/%d prices", priceHistory.size(), config.movingAveragePeriod()));
        }
        
        BigDecimal movingAverage = calculateMovingAverage();
        BigDecimal currentPrice = inputData.currentPrice();
        BigDecimal priceDeviation = calculatePriceDeviation(currentPrice, movingAverage);

        // LOG DETALHADO para debug
        System.out.println(String.format(
            "[SMA-STRATEGY] Price: %s | MA(%d): %s | Deviation: %.6f%% | BuyThreshold: %.6f%% | SellThreshold: %.6f%%",
            currentPrice,
            config.movingAveragePeriod(),
            movingAverage,
            priceDeviation.multiply(BigDecimal.valueOf(100)).doubleValue(),
            config.buyThreshold().multiply(BigDecimal.valueOf(100)).doubleValue(),
            config.sellThreshold().multiply(BigDecimal.valueOf(100)).doubleValue()
        ));

        String reasoning = String.format("Price: %s, MA(%d): %s, Deviation: %.6f%%",
                                        currentPrice, config.movingAveragePeriod(), movingAverage,
                                        priceDeviation.multiply(BigDecimal.valueOf(100)).doubleValue());

        // Verificar timeout de posições abertas - força venda para liberar capital
        Optional<StrategyOutputDto> timeoutSell = checkPositionTimeout(portfolioContext, reasoning);
        if (timeoutSell.isPresent()) {
            return timeoutSell.get();
        }

        // Decisão baseada no desvio da média móvel
        if (priceDeviation.compareTo(config.sellThreshold()) >= 0) {
            // Preço muito acima da média - VENDER
            BigDecimal quantity = calculateSellQuantity(portfolioContext);

            // Se não tem posição para vender, HOLD
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                System.out.println("[SMA-STRATEGY] --- HOLD (SELL signal but no position to sell)");
                return StrategyOutputDto.hold(STRATEGY_NAME, reasoning + " - Price above MA but no position to sell");
            }

            BigDecimal confidence = calculateConfidence(priceDeviation.abs(), config.sellThreshold().abs());
            System.out.println(String.format("[SMA-STRATEGY] >>> SELL SIGNAL! Quantity: %s, Confidence: %s, HasPosition: %s",
                quantity, confidence, portfolioContext.hasPosition()));

            return StrategyOutputDto.sell(STRATEGY_NAME, confidence, quantity,
                                           reasoning + " - Price above MA threshold");

        } else if (priceDeviation.compareTo(config.buyThreshold()) <= 0) {
            // Preço muito abaixo da média - COMPRAR
            BigDecimal quantity = calculateBuyQuantity(inputData.currentPrice(), portfolioContext);

            // Se não tem saldo para comprar, HOLD
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                System.out.println("[SMA-STRATEGY] --- HOLD (BUY signal but insufficient balance)");
                return StrategyOutputDto.hold(STRATEGY_NAME, reasoning + " - Price below MA but insufficient balance");
            }

            BigDecimal confidence = calculateConfidence(priceDeviation.abs(), config.buyThreshold().abs());
            System.out.println(String.format("[SMA-STRATEGY] >>> BUY SIGNAL! Quantity: %s, Confidence: %s, AvailableBalance: %s",
                quantity, confidence, portfolioContext.availableBalance().amount()));

            return StrategyOutputDto.buy(STRATEGY_NAME, confidence, quantity,
                                          reasoning + " - Price below MA threshold");

        } else {
            // Preço próximo da média - HOLD
            System.out.println("[SMA-STRATEGY] --- HOLD (price within threshold range)");
            return StrategyOutputDto.hold(STRATEGY_NAME, reasoning + " - Price near MA");
        }
    }
    
    private BigDecimal calculateMovingAverage() {
        BigDecimal sum = priceHistory.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(priceHistory.size()), 8, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculatePriceDeviation(BigDecimal currentPrice, BigDecimal movingAverage) {
        if (movingAverage.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return currentPrice.subtract(movingAverage)
                .divide(movingAverage, 8, RoundingMode.HALF_UP);
    }
    
    /**
     * Calcula confidence baseado na magnitude do desvio vs threshold
     * Quanto maior o desvio em relação ao threshold, maior a confidence
     */
    private BigDecimal calculateConfidence(BigDecimal deviationMagnitude, BigDecimal threshold) {
        if (threshold.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ONE;
        }
        
        // Confidence = min(1.0, deviationMagnitude / threshold)
        // Ex: Se threshold é 5% e deviation é 10%, confidence = 1.0 (100%)
        //     Se threshold é 5% e deviation é 2.5%, confidence = 0.5 (50%)
        BigDecimal ratio = deviationMagnitude.divide(threshold, 4, RoundingMode.HALF_UP);
        return ratio.min(BigDecimal.ONE);
    }
    
    /**
     * Verifica se há posições abertas além do timeout configurado.
     * Se houver, força venda de toda a posição para liberar capital.
     */
    private Optional<StrategyOutputDto> checkPositionTimeout(PortfolioContextDto portfolioContext, String reasoning) {
        if (config.positionTimeoutMinutes() == 0) {
            return Optional.empty();
        }

        if (!portfolioContext.hasOpenTransactions() || !portfolioContext.hasPosition()) {
            return Optional.empty();
        }

        Optional<OpenBuyEntryDto> oldest = portfolioContext.getOldestOpenTransaction();
        if (oldest.isEmpty()) {
            return Optional.empty();
        }

        Duration elapsed = Duration.between(oldest.get().executedAt(), Instant.now());
        long timeoutMinutes = config.positionTimeoutMinutes();

        if (elapsed.toMinutes() < timeoutMinutes) {
            return Optional.empty();
        }

        // Timeout atingido - vender toda a posição
        BigDecimal fullQuantity = portfolioContext.position().getQuantity().amount();

        System.out.println(String.format(
            "[SMA-STRATEGY] >>> TIMEOUT SELL! Position open for %d min (limit: %d min). Selling full quantity: %s",
            elapsed.toMinutes(), timeoutMinutes, fullQuantity));

        return Optional.of(StrategyOutputDto.sell(STRATEGY_NAME, BigDecimal.ONE, fullQuantity,
                reasoning + String.format(" - Position timeout: %d min > %d min limit",
                        elapsed.toMinutes(), timeoutMinutes)));
    }

    /**
     * Calcula quantity para compra baseada no portfolio context e allocation da strategy
     */
    private BigDecimal calculateBuyQuantity(BigDecimal currentPrice, PortfolioContextDto portfolioContext) {
        // Verificar se há saldo mínimo
        if (!portfolioContext.hasMinimumBalance()) {
            return BigDecimal.ZERO;
        }
        
        // Calcular valor baseado na alocação configurada na strategy (ex: 10% do capital)
        BigDecimal totalCapital = portfolioContext.totalCapital().amount();
        BigDecimal allocationValue = totalCapital.multiply(config.allocationPercentage());
        
        // Verificar se não excede saldo disponível
        BigDecimal availableBalance = portfolioContext.availableBalance().amount();
        BigDecimal operationValue = allocationValue.min(availableBalance);
        
        // Verificar valor mínimo de operação
        if (operationValue.compareTo(portfolioContext.minimumOperationAmount()) < 0) {
            return BigDecimal.ZERO;
        }
        
        // Calcular quantity = valor / preço
        return operationValue.divide(currentPrice, 8, RoundingMode.DOWN);
    }
    
    /**
     * Calcula quantity para venda baseada no portfolio context e allocation da strategy
     */
    private BigDecimal calculateSellQuantity(PortfolioContextDto portfolioContext) {
        // Verificar se existe posição
        if (!portfolioContext.hasPosition()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal availableQuantity = portfolioContext.position().getQuantity().amount();
        
        // Vender percentual configurado da posição (ex: 10% da posição atual)
        BigDecimal sellQuantity = availableQuantity.multiply(config.allocationPercentage());
        
        return sellQuantity.setScale(8, RoundingMode.DOWN);
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

    public int getCurrentHistorySize() {
        return priceHistory.size();
    }
    
    public List<BigDecimal> getPriceHistory() {
        return List.copyOf(priceHistory);
    }
    
    public void clearHistory() {
        priceHistory.clear();
    }
}