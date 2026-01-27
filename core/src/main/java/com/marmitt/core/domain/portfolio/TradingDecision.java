package com.marmitt.core.domain.portfolio;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.dto.strategy.StrategyInputDto;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.enums.AssetType;
import com.marmitt.core.enums.TradingAction;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Builder
public record TradingDecision(
        TradingAction decision,
        Symbol symbol,
        BigDecimal quantity,        // Quantidade final calculada pelo Portfolio
        Asset price,               // Preço com tipo correto (fiat/stablecoin/crypto)
        Asset estimatedTotal,      // Valor total estimado da operação
        String reasoning,          // Combinação do reasoning da strategy + risk assessment
        BigDecimal confidence,     // Confidence da strategy
        Instant timestamp
) {
    
    public TradingDecision {
        Objects.requireNonNull(decision, "Decision cannot be null");
        Objects.requireNonNull(symbol, "Symbol cannot be null");
        Objects.requireNonNull(reasoning, "Reasoning cannot be null");
        Objects.requireNonNull(timestamp, "Timestamp cannot be null");
        
        if (shouldExecute()) {
            Objects.requireNonNull(quantity, "Quantity cannot be null for trade decisions");
            Objects.requireNonNull(price, "Price cannot be null for trade decisions");
            Objects.requireNonNull(estimatedTotal, "Estimated total cannot be null for trade decisions");
            
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Quantity must be positive for trade decisions");
            }
        }
    }
    
    public static TradingDecision hold(Symbol symbol, String reasoning) {
        return new TradingDecision(
            TradingAction.SHOULD_HOLD,
            symbol,
            null,
            null, 
            null,
            reasoning,
            BigDecimal.ZERO,
            Instant.now()
        );
    }
    
    public static TradingDecision buy(Symbol symbol, BigDecimal quantity, Asset price, 
                                     String reasoning, BigDecimal confidence) {
        Asset estimatedTotal = price.multiply(quantity);
        return new TradingDecision(
            TradingAction.SHOULD_BUY,
            symbol,
            quantity,
            price,
            estimatedTotal,
            reasoning,
            confidence,
            Instant.now()
        );
    }
    
    public static TradingDecision sell(Symbol symbol, BigDecimal quantity, Asset price, 
                                      String reasoning, BigDecimal confidence) {
        Asset estimatedTotal = price.multiply(quantity);
        return new TradingDecision(
            TradingAction.SHOULD_SELL,
            symbol,
            quantity,
            price,
            estimatedTotal,
            reasoning,
            confidence,
            Instant.now()
        );
    }
    
    /**
     * Cria TradingDecision a partir do StrategyOutput
     * Strategy já calculou quantity absoluta considerando portfolio context
     */
    public static TradingDecision fromStrategy(StrategyInputDto input, StrategyOutputDto output) {
        Objects.requireNonNull(input, "StrategyInput cannot be null");
        Objects.requireNonNull(output, "StrategyOutput cannot be null");
        
        if (output.shouldHold()) {
            return TradingDecision.hold(input.symbol(), output.reasoning());
        }
        
        if (output.quantity() == null || output.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            return TradingDecision.hold(input.symbol(),
                    output.reasoning() + " - Strategy returned invalid quantity");
        }
        
        // Criar Asset com tipo correto baseado na quote currency
        Asset price = createPriceAsset(input.currentPrice(), input.symbol().getQuoteAsset());
        
        if (output.decision() == TradingAction.SHOULD_BUY) {
            return TradingDecision.buy(input.symbol(), output.quantity(), price,
                    output.reasoning(), output.confidence());
        } else if (output.decision() == TradingAction.SHOULD_SELL) {
            return TradingDecision.sell(input.symbol(), output.quantity(), price,
                    output.reasoning(), output.confidence());
        }
        
        return TradingDecision.hold(input.symbol(), "Unknown strategy decision: " + output.decision());
    }
    
    /**
     * Cria Asset com tipo correto baseado na moeda de cotação
     */
    private static Asset createPriceAsset(BigDecimal amount, String quoteCurrency) {
        AssetType assetType = TradingAssetsConfig.classifyAssetType(quoteCurrency);
        
        return switch (assetType) {
            case FIAT -> Asset.fiat(amount, quoteCurrency);
            case STABLECOIN -> Asset.stableCoin(amount, quoteCurrency);
            case CRYPTOCURRENCY -> Asset.crypto(amount, quoteCurrency);
            case NOT_SUPPORTED -> throw new IllegalArgumentException(
                "Unsupported quote currency: " + quoteCurrency + 
                ". Add it to TradingAssetsConfig or use supported currencies only."
            );
        };
    }
    
    public boolean shouldExecute() {
        return decision == TradingAction.SHOULD_BUY || decision == TradingAction.SHOULD_SELL;
    }
    
    public boolean shouldHold() {
        return decision == TradingAction.SHOULD_HOLD;
    }
    
    public boolean isHighConfidence() {
        return confidence != null && confidence.compareTo(new BigDecimal("0.7")) >= 0;
    }
    
    public boolean isBuy() {
        return decision == TradingAction.SHOULD_BUY;
    }
    
    public boolean isSell() {
        return decision == TradingAction.SHOULD_SELL;
    }
}