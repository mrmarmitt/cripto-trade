package com.marmitt.ctrade.domain.entity;

import com.marmitt.ctrade.domain.valueobject.TradeStatus;
import com.marmitt.ctrade.domain.valueobject.TradeType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "trades")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trade {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "strategy_name", nullable = false)
    private String strategyName;
    
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "baseCurrency", column = @Column(name = "base_currency")),
        @AttributeOverride(name = "quoteCurrency", column = @Column(name = "quote_currency"))
    })
    private TradingPair pair;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "trade_type", nullable = false)
    private TradeType type;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "trade_status", nullable = false)
    private TradeStatus status;
    
    // Entrada
    @Column(name = "entry_price", precision = 18, scale = 8)
    private BigDecimal entryPrice;
    
    @Column(name = "entry_quantity", precision = 18, scale = 8)
    private BigDecimal entryQuantity;
    
    @Column(name = "entry_time")
    private LocalDateTime entryTime;
    
    @Column(name = "entry_order_id")
    private String entryOrderId;
    
    // Saída
    @Column(name = "exit_price", precision = 18, scale = 8)
    private BigDecimal exitPrice;
    
    @Column(name = "exit_quantity", precision = 18, scale = 8)
    private BigDecimal exitQuantity;
    
    @Column(name = "exit_time")
    private LocalDateTime exitTime;
    
    @Column(name = "exit_order_id")
    private String exitOrderId;
    
    // Quantidade restante para trades parcialmente fechados
    @Column(name = "remaining_quantity", precision = 18, scale = 8)
    private BigDecimal remainingQuantity;
    
    // Valor total investido
    @Column(name = "invested_amount", precision = 18, scale = 8)
    private BigDecimal investedAmount;
    
    // P&L
    @Column(name = "realized_pnl", precision = 18, scale = 8)
    private BigDecimal realizedPnL;
    
    @Column(name = "unrealized_pnl", precision = 18, scale = 8)
    private BigDecimal unrealizedPnL;
    
    @Column(name = "commission", precision = 18, scale = 8)
    private BigDecimal commission;
    
    // Métricas
    @Column(name = "holding_period_seconds")
    private Long holdingPeriodSeconds;
    
    @Column(name = "max_drawdown", precision = 18, scale = 8)
    private BigDecimal maxDrawdown;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        
        // Inicializar valores padrão
        if (realizedPnL == null) {
            realizedPnL = BigDecimal.ZERO;
        }
        if (unrealizedPnL == null) {
            unrealizedPnL = BigDecimal.ZERO;
        }
        if (commission == null) {
            commission = BigDecimal.ZERO;
        }
        if (maxDrawdown == null) {
            maxDrawdown = BigDecimal.ZERO;
        }
        
        // Inicializar quantidade restante
        if (remainingQuantity == null && entryQuantity != null) {
            remainingQuantity = entryQuantity;
        }
        
        // Calcular valor investido
        if (investedAmount == null && entryPrice != null && entryQuantity != null) {
            investedAmount = entryPrice.multiply(entryQuantity);
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        
        // Calcular holding period se o trade foi fechado
        if (status == TradeStatus.CLOSED && entryTime != null && exitTime != null) {
            holdingPeriodSeconds = Duration.between(entryTime, exitTime).getSeconds();
        }
    }
    
    /**
     * Verifica se o trade está aberto
     */
    public boolean isOpen() {
        return status == TradeStatus.OPEN;
    }
    
    /**
     * Verifica se o trade está fechado
     */
    public boolean isClosed() {
        return status == TradeStatus.CLOSED;
    }
    
    /**
     * Verifica se o trade está parcialmente fechado
     */
    public boolean isPartialClosed() {
        return status == TradeStatus.PARTIAL_CLOSED;
    }
    
    /**
     * Calcula o P&L total (realizado + não realizado)
     */
    public BigDecimal getTotalPnL() {
        BigDecimal realized = realizedPnL != null ? realizedPnL : BigDecimal.ZERO;
        BigDecimal unrealized = unrealizedPnL != null ? unrealizedPnL : BigDecimal.ZERO;
        return realized.add(unrealized);
    }
    
    /**
     * Retorna o valor total investido
     */
    public BigDecimal getInvestedAmount() {
        // Usa o valor salvo no campo se disponível
        if (investedAmount != null) {
            return investedAmount;
        }
        
        // Calcula dinamicamente se os dados estão disponíveis
        if (entryPrice == null || entryQuantity == null) {
            return BigDecimal.ZERO;
        }
        return entryPrice.multiply(entryQuantity);
    }
    
    /**
     * Calcula o retorno percentual do trade
     */
    public BigDecimal getReturnPercentage() {
        BigDecimal invested = getInvestedAmount();
        if (invested.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return getTotalPnL()
                .divide(invested, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
    
    /**
     * Calcula a duração da posição em horas
     */
    public Duration getHoldingPeriod() {
        if (holdingPeriodSeconds != null) {
            return Duration.ofSeconds(holdingPeriodSeconds);
        }
        
        if (entryTime != null) {
            LocalDateTime endTime = exitTime != null ? exitTime : LocalDateTime.now();
            return Duration.between(entryTime, endTime);
        }
        
        return Duration.ZERO;
    }
    
    /**
     * Verifica se o trade foi lucrativo
     */
    public boolean isProfitable() {
        return getTotalPnL().compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * Verifica se o trade teve prejuízo
     */
    public boolean isLoss() {
        return getTotalPnL().compareTo(BigDecimal.ZERO) < 0;
    }
}