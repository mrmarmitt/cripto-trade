package com.marmitt.core.dto.order;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.portfolio.Asset;
import com.marmitt.core.domain.portfolio.TradingDecision;
import com.marmitt.core.enums.TradingAction;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Representa uma solicitação de ordem para execução
 * Contém todos os dados necessários para executar uma operação de compra ou venda
 */
@Builder
public record OrderRequest(
        UUID orderId,
        UUID portfolioId,
        TradingAction action,         // BUY ou SELL
        Symbol symbol,               // Par de trading (ex: BTCUSDT)
        BigDecimal quantity,         // Quantidade do ativo base a ser negociada
        Asset targetPrice,           // Preço alvo para execução
        Asset maxSlippage,           // Slippage máximo aceitável
        String strategy,             // Nome da estratégia que originou a ordem
        String reasoning,            // Justificativa da decisão
        BigDecimal confidence,       // Nível de confiança da estratégia (0.0 - 1.0)
        Instant requestedAt,         // Timestamp da solicitação
        Integer timeoutSeconds       // Timeout para execução em segundos
) {
    
    public OrderRequest {
        Objects.requireNonNull(orderId, "Order ID cannot be null");
        Objects.requireNonNull(portfolioId, "Portfolio ID cannot be null");
        Objects.requireNonNull(action, "Action cannot be null");
        Objects.requireNonNull(symbol, "Symbol cannot be null");
        Objects.requireNonNull(quantity, "Quantity cannot be null");
        Objects.requireNonNull(targetPrice, "Target price cannot be null");
        Objects.requireNonNull(strategy, "Strategy cannot be null");
        Objects.requireNonNull(reasoning, "Reasoning cannot be null");
        Objects.requireNonNull(requestedAt, "Requested at cannot be null");
        
        if (action == TradingAction.SHOULD_HOLD) {
            throw new IllegalArgumentException("OrderRequest cannot be created for HOLD action");
        }
        
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        
        if (targetPrice.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Target price must be positive");
        }
        
        if (confidence != null && (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalArgumentException("Confidence must be between 0.0 and 1.0");
        }
        
        if (timeoutSeconds != null && timeoutSeconds <= 0) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
    }
    
    /**
     * Cria OrderRequest a partir de uma TradingDecision
     */
    public static OrderRequest fromTradingDecision(TradingDecision decision, UUID portfolioId, String strategy) {
        Objects.requireNonNull(decision, "Trading decision cannot be null");
        Objects.requireNonNull(portfolioId, "Portfolio ID cannot be null");
        Objects.requireNonNull(strategy, "Strategy cannot be null");
        
        if (!decision.shouldExecute()) {
            throw new IllegalArgumentException("Cannot create OrderRequest from HOLD decision");
        }
        
        return OrderRequest.builder()
            .orderId(UUID.randomUUID())
            .portfolioId(portfolioId)
            .action(decision.decision())
            .symbol(decision.symbol())
            .quantity(decision.quantity())
            .targetPrice(decision.price())
            .maxSlippage(calculateDefaultSlippage(decision.price()))
            .strategy(strategy)
            .reasoning(decision.reasoning())
            .confidence(decision.confidence())
            .requestedAt(Instant.now())
            .timeoutSeconds(30) // Default timeout de 30 segundos
            .build();
    }
    
    /**
     * Calcula slippage padrão baseado no preço (0.1% para a maioria dos casos)
     */
    private static Asset calculateDefaultSlippage(Asset price) {
        BigDecimal slippagePercentage = new BigDecimal("0.001"); // 0.1%
        BigDecimal slippageAmount = price.amount().multiply(slippagePercentage);
        return Asset.of(slippageAmount, price.currency());
    }
    
    /**
     * Calcula o preço máximo aceitável para compra (targetPrice + slippage)
     */
    public Asset getMaxBuyPrice() {
        if (action != TradingAction.SHOULD_BUY) {
            throw new IllegalStateException("Max buy price only applicable for BUY orders");
        }
        return maxSlippage != null ? targetPrice.add(maxSlippage) : targetPrice;
    }
    
    /**
     * Calcula o preço mínimo aceitável para venda (targetPrice - slippage)
     */
    public Asset getMinSellPrice() {
        if (action != TradingAction.SHOULD_SELL) {
            throw new IllegalStateException("Min sell price only applicable for SELL orders");
        }
        return maxSlippage != null ? targetPrice.subtract(maxSlippage) : targetPrice;
    }
    
    /**
     * Calcula valor total estimado da ordem
     */
    public Asset getEstimatedTotal() {
        return targetPrice.multiply(quantity);
    }
    
    /**
     * Verifica se a ordem é de compra
     */
    public boolean isBuyOrder() {
        return action == TradingAction.SHOULD_BUY;
    }
    
    /**
     * Verifica se a ordem é de venda
     */
    public boolean isSellOrder() {
        return action == TradingAction.SHOULD_SELL;
    }
    
    /**
     * Verifica se a ordem tem alta confiança (>= 70%)
     */
    public boolean isHighConfidence() {
        return confidence != null && confidence.compareTo(new BigDecimal("0.7")) >= 0;
    }
    
    /**
     * Verifica se a ordem expirou baseada no timeout
     */
    public boolean isExpired() {
        if (timeoutSeconds == null) return false;
        
        Instant expirationTime = requestedAt.plusSeconds(timeoutSeconds);
        return Instant.now().isAfter(expirationTime);
    }
}