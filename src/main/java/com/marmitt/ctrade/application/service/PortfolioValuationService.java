package com.marmitt.ctrade.application.service;

import com.marmitt.ctrade.application.service.PriceCacheService;
import com.marmitt.ctrade.domain.entity.Portfolio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioValuationService {
    
    private final PriceCacheService priceCacheService;
    
    private static final String BASE_CURRENCY = "USDT";
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int SCALE = 8;
    
    /**
     * Calcula o valor total do portfolio em USDT
     */
    public BigDecimal calculateTotalValue(Portfolio portfolio) {
        if (portfolio == null || portfolio.getHoldings() == null) {
            log.warn("Portfolio is null or has no holdings");
            return ZERO;
        }
        
        BigDecimal totalValue = ZERO;
        
        for (Map.Entry<String, BigDecimal> holding : portfolio.getHoldings().entrySet()) {
            String currency = holding.getKey();
            BigDecimal amount = holding.getValue();
            
            if (amount == null || amount.compareTo(ZERO) <= 0) {
                continue;
            }
            
            BigDecimal valueInUSDT = getValueInUSDT(currency, amount);
            totalValue = totalValue.add(valueInUSDT);
            
            log.debug("Portfolio holding: {} {} = {} USDT", amount, currency, valueInUSDT);
        }
        
        log.debug("Portfolio total value: {} USDT", totalValue);
        return totalValue.setScale(SCALE, RoundingMode.HALF_UP);
    }
    
    /**
     * Converte valor de uma moeda para USDT
     */
    public BigDecimal getValueInUSDT(String currency, BigDecimal amount) {
        if (amount == null || amount.compareTo(ZERO) <= 0) {
            return ZERO;
        }
        
        // Se já é USDT, retorna o valor
        if (BASE_CURRENCY.equalsIgnoreCase(currency)) {
            return amount;
        }
        
        // Busca preço da moeda em USDT
        String symbol = currency + BASE_CURRENCY;
        Optional<BigDecimal> priceOpt = priceCacheService.getLatestPrice(symbol);
        
        if (priceOpt.isEmpty()) {
            log.warn("No price available for {}, cannot calculate USDT value", symbol);
            return ZERO;
        }
        
        BigDecimal price = priceOpt.get();
        BigDecimal value = amount.multiply(price);
        
        log.debug("Converting {} {} to USDT: {} * {} = {}", amount, currency, amount, price, value);
        
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }
    
    /**
     * Calcula percentual de alocação de cada ativo no portfolio
     */
    public Map<String, BigDecimal> calculateAllocationPercentages(Portfolio portfolio) {
        Map<String, BigDecimal> allocations = new HashMap<>();
        
        BigDecimal totalValue = calculateTotalValue(portfolio);
        
        if (totalValue.compareTo(ZERO) == 0) {
            log.warn("Portfolio total value is zero, cannot calculate allocations");
            return allocations;
        }
        
        for (Map.Entry<String, BigDecimal> holding : portfolio.getHoldings().entrySet()) {
            String currency = holding.getKey();
            BigDecimal amount = holding.getValue();
            
            if (amount == null || amount.compareTo(ZERO) <= 0) {
                allocations.put(currency, ZERO);
                continue;
            }
            
            BigDecimal valueInUSDT = getValueInUSDT(currency, amount);
            BigDecimal percentage = valueInUSDT
                    .divide(totalValue, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
            
            allocations.put(currency, percentage);
            
            log.debug("Asset allocation: {} = {}% ({}% USDT)", currency, percentage, valueInUSDT);
        }
        
        return allocations;
    }
    
    /**
     * Calcula valor de um portfolio específico de uma estratégia
     */
    public BigDecimal calculateStrategyPortfolioValue(Map<String, BigDecimal> strategyHoldings) {
        if (strategyHoldings == null || strategyHoldings.isEmpty()) {
            return ZERO;
        }
        
        BigDecimal totalValue = ZERO;
        
        for (Map.Entry<String, BigDecimal> holding : strategyHoldings.entrySet()) {
            String currency = holding.getKey();
            BigDecimal amount = holding.getValue();
            
            BigDecimal valueInUSDT = getValueInUSDT(currency, amount);
            totalValue = totalValue.add(valueInUSDT);
        }
        
        return totalValue.setScale(SCALE, RoundingMode.HALF_UP);
    }
    
    /**
     * Calcula diversificação do portfolio (índice Herfindahl-Hirschman)
     */
    public BigDecimal calculateDiversificationIndex(Portfolio portfolio) {
        Map<String, BigDecimal> allocations = calculateAllocationPercentages(portfolio);
        
        if (allocations.isEmpty()) {
            return ZERO;
        }
        
        // Calcula HHI (soma dos quadrados das participações)
        BigDecimal hhi = allocations.values().stream()
                .map(percentage -> percentage.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP))
                .map(fraction -> fraction.pow(2))
                .reduce(ZERO, BigDecimal::add);
        
        // Converte para índice de diversificação (1 - HHI)
        // Quanto maior, mais diversificado (0 = concentrado, 1 = perfeitamente diversificado)
        BigDecimal diversificationIndex = BigDecimal.ONE.subtract(hhi)
                .setScale(4, RoundingMode.HALF_UP);
        
        log.debug("Portfolio diversification index: {} (HHI: {})", diversificationIndex, hhi);
        
        return diversificationIndex;
    }
    
    /**
     * Identifica os maiores holdings do portfolio
     */
    public Map<String, BigDecimal> getTopHoldings(Portfolio portfolio, int limit) {
        Map<String, BigDecimal> allocations = calculateAllocationPercentages(portfolio);
        
        return allocations.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(limit)
                .collect(HashMap::new, 
                        (map, entry) -> map.put(entry.getKey(), entry.getValue()),
                        HashMap::putAll);
    }
    
    /**
     * Calcula valor mínimo necessário para uma operação
     */
    public BigDecimal calculateMinimumTradeValue(String currency, BigDecimal minUSDTValue) {
        if (BASE_CURRENCY.equalsIgnoreCase(currency)) {
            return minUSDTValue;
        }
        
        String symbol = currency + BASE_CURRENCY;
        Optional<BigDecimal> priceOpt = priceCacheService.getLatestPrice(symbol);
        
        if (priceOpt.isEmpty()) {
            log.warn("No price available for {}, cannot calculate minimum trade value", symbol);
            return ZERO;
        }
        
        BigDecimal price = priceOpt.get();
        
        if (price.compareTo(ZERO) == 0) {
            return ZERO;
        }
        
        return minUSDTValue.divide(price, SCALE, RoundingMode.HALF_UP);
    }
    
    /**
     * Verifica se há saldo suficiente para uma operação
     */
    public boolean hasSufficientBalance(Portfolio portfolio, String currency, BigDecimal requiredAmount) {
        if (portfolio == null || portfolio.getHoldings() == null) {
            return false;
        }
        
        BigDecimal currentBalance = portfolio.getBalance(currency);
        
        if (currentBalance == null) {
            return false;
        }
        
        boolean sufficient = currentBalance.compareTo(requiredAmount) >= 0;
        
        log.debug("Balance check for {} {}: current={}, required={}, sufficient={}", 
                requiredAmount, currency, currentBalance, requiredAmount, sufficient);
        
        return sufficient;
    }
    
    /**
     * Calcula o valor máximo que pode ser investido em USDT
     */
    public BigDecimal getMaxInvestableAmount(Portfolio portfolio, BigDecimal reservePercentage) {
        BigDecimal totalValue = calculateTotalValue(portfolio);
        
        if (totalValue.compareTo(ZERO) == 0) {
            return ZERO;
        }
        
        // Calcula valor de reserva a manter
        BigDecimal reserveAmount = totalValue.multiply(reservePercentage)
                .setScale(SCALE, RoundingMode.HALF_UP);
        
        BigDecimal maxInvestable = totalValue.subtract(reserveAmount);
        
        // Não pode ser negativo
        if (maxInvestable.compareTo(ZERO) < 0) {
            maxInvestable = ZERO;
        }
        
        log.debug("Max investable amount: {} USDT (total: {}, reserve: {}%)", 
                maxInvestable, totalValue, reservePercentage.multiply(BigDecimal.valueOf(100)));
        
        return maxInvestable.setScale(SCALE, RoundingMode.HALF_UP);
    }
    
    /**
     * Simula o impacto de uma operação no portfolio
     */
    public PortfolioImpact simulateTradeImpact(Portfolio portfolio, String buyCurrency, 
                                             String sellCurrency, BigDecimal sellAmount) {
        
        BigDecimal currentTotalValue = calculateTotalValue(portfolio);
        Map<String, BigDecimal> currentAllocations = calculateAllocationPercentages(portfolio);
        
        // Simula a operação
        Portfolio simulatedPortfolio = new Portfolio(new HashMap<>(portfolio.getHoldings()));
        
        // Vende a moeda de origem
        BigDecimal currentSellBalance = simulatedPortfolio.getBalance(sellCurrency);
        simulatedPortfolio.subtractFromBalance(sellCurrency, sellAmount);
        
        // Compra a moeda de destino (convertendo valor)
        BigDecimal buyAmount = getValueInCurrency(sellCurrency, sellAmount, buyCurrency);
        simulatedPortfolio.addToBalance(buyCurrency, buyAmount);
        
        BigDecimal newTotalValue = calculateTotalValue(simulatedPortfolio);
        Map<String, BigDecimal> newAllocations = calculateAllocationPercentages(simulatedPortfolio);
        
        return PortfolioImpact.builder()
                .currentTotalValue(currentTotalValue)
                .newTotalValue(newTotalValue)
                .valueChange(newTotalValue.subtract(currentTotalValue))
                .currentAllocations(currentAllocations)
                .newAllocations(newAllocations)
                .tradeCurrency(buyCurrency)
                .tradeAmount(buyAmount)
                .build();
    }
    
    private BigDecimal getValueInCurrency(String fromCurrency, BigDecimal amount, String toCurrency) {
        // Converte para USDT primeiro
        BigDecimal usdtValue = getValueInUSDT(fromCurrency, amount);
        
        // Se destino é USDT, retorna
        if (BASE_CURRENCY.equalsIgnoreCase(toCurrency)) {
            return usdtValue;
        }
        
        // Converte de USDT para moeda de destino
        String symbol = toCurrency + BASE_CURRENCY;
        Optional<BigDecimal> priceOpt = priceCacheService.getLatestPrice(symbol);
        
        if (priceOpt.isEmpty()) {
            log.warn("No price available for {}", symbol);
            return ZERO;
        }
        
        BigDecimal price = priceOpt.get();
        
        if (price.compareTo(ZERO) == 0) {
            return ZERO;
        }
        
        return usdtValue.divide(price, SCALE, RoundingMode.HALF_UP);
    }
    
    /**
     * Value object para resultado de simulação de impacto
     */
    public static class PortfolioImpact {
        private final BigDecimal currentTotalValue;
        private final BigDecimal newTotalValue;
        private final BigDecimal valueChange;
        private final Map<String, BigDecimal> currentAllocations;
        private final Map<String, BigDecimal> newAllocations;
        private final String tradeCurrency;
        private final BigDecimal tradeAmount;
        
        private PortfolioImpact(BigDecimal currentTotalValue, BigDecimal newTotalValue, 
                              BigDecimal valueChange, Map<String, BigDecimal> currentAllocations,
                              Map<String, BigDecimal> newAllocations, String tradeCurrency, 
                              BigDecimal tradeAmount) {
            this.currentTotalValue = currentTotalValue;
            this.newTotalValue = newTotalValue;
            this.valueChange = valueChange;
            this.currentAllocations = currentAllocations;
            this.newAllocations = newAllocations;
            this.tradeCurrency = tradeCurrency;
            this.tradeAmount = tradeAmount;
        }
        
        public static PortfolioImpactBuilder builder() {
            return new PortfolioImpactBuilder();
        }
        
        // Getters
        public BigDecimal getCurrentTotalValue() { return currentTotalValue; }
        public BigDecimal getNewTotalValue() { return newTotalValue; }
        public BigDecimal getValueChange() { return valueChange; }
        public Map<String, BigDecimal> getCurrentAllocations() { return currentAllocations; }
        public Map<String, BigDecimal> getNewAllocations() { return newAllocations; }
        public String getTradeCurrency() { return tradeCurrency; }
        public BigDecimal getTradeAmount() { return tradeAmount; }
        
        public static class PortfolioImpactBuilder {
            private BigDecimal currentTotalValue;
            private BigDecimal newTotalValue;
            private BigDecimal valueChange;
            private Map<String, BigDecimal> currentAllocations;
            private Map<String, BigDecimal> newAllocations;
            private String tradeCurrency;
            private BigDecimal tradeAmount;
            
            public PortfolioImpactBuilder currentTotalValue(BigDecimal currentTotalValue) {
                this.currentTotalValue = currentTotalValue;
                return this;
            }
            
            public PortfolioImpactBuilder newTotalValue(BigDecimal newTotalValue) {
                this.newTotalValue = newTotalValue;
                return this;
            }
            
            public PortfolioImpactBuilder valueChange(BigDecimal valueChange) {
                this.valueChange = valueChange;
                return this;
            }
            
            public PortfolioImpactBuilder currentAllocations(Map<String, BigDecimal> currentAllocations) {
                this.currentAllocations = currentAllocations;
                return this;
            }
            
            public PortfolioImpactBuilder newAllocations(Map<String, BigDecimal> newAllocations) {
                this.newAllocations = newAllocations;
                return this;
            }
            
            public PortfolioImpactBuilder tradeCurrency(String tradeCurrency) {
                this.tradeCurrency = tradeCurrency;
                return this;
            }
            
            public PortfolioImpactBuilder tradeAmount(BigDecimal tradeAmount) {
                this.tradeAmount = tradeAmount;
                return this;
            }
            
            public PortfolioImpact build() {
                return new PortfolioImpact(currentTotalValue, newTotalValue, valueChange,
                        currentAllocations, newAllocations, tradeCurrency, tradeAmount);
            }
        }
    }
}