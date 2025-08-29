package com.marmitt.ctrade.domain.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StrategyMetrics {
    
    private String strategyName;
    
    // P&L Metrics
    private BigDecimal totalPnL;                    // P&L total (realizado + não realizado)
    private BigDecimal realizedPnL;                 // P&L realizado (apenas trades fechados)
    private BigDecimal unrealizedPnL;               // P&L não realizado (trades abertos)
    private BigDecimal totalReturn;                 // Retorno percentual total
    private BigDecimal totalCommission;             // Total de taxas pagas
    
    // Trade Statistics
    private Integer totalTrades;                    // Número total de trades
    private Integer openTrades;                     // Trades atualmente abertos (OPEN)
    private Integer partiallyClosedTrades;          // Trades parcialmente fechados (PARTIAL_CLOSED)
    private Integer closedTrades;                   // Trades completamente fechados (CLOSED)
    private Integer winningTrades;                  // Trades lucrativos
    private Integer losingTrades;                   // Trades com prejuízo
    private Integer neutralTrades;                  // Trades sem ganho nem perda (P&L = 0)
    
    // Order Statistics
    private Integer totalOrders;                    // Total de ordens enviadas
    private Integer successfulOrders;               // Ordens executadas (FILLED)
    private Integer failedOrders;                   // Ordens canceladas/rejeitadas
    private Integer pendingOrders;                  // Ordens ainda pendentes
    
    // Performance Ratios
    private Double winRate;                         // Taxa de acerto (winning trades / total trades)
    private Double lossRate;                        // Taxa de erro (losing trades / total trades)
    private BigDecimal avgWin;                      // Lucro médio por trade vencedor
    private BigDecimal avgLoss;                     // Prejuízo médio por trade perdedor
    private BigDecimal avgPnL;                      // P&L médio por trade
    private BigDecimal profitFactor;                // Total de lucros / Total de prejuízos
    
    // Risk Metrics
    private BigDecimal maxDrawdown;                 // Maior drawdown observado
    private BigDecimal maxDrawdownPercentage;       // Maior drawdown em percentual
    private BigDecimal currentDrawdown;             // Drawdown atual
    private BigDecimal sharpeRatio;                 // Sharpe ratio
    private BigDecimal sortinoRatio;                // Sortino ratio
    private BigDecimal calmarRatio;                 // Calmar ratio (return/max drawdown)
    
    // Time Metrics
    private Duration avgHoldingPeriod;              // Tempo médio de posição
    private Duration maxHoldingPeriod;              // Maior tempo em posição
    private Duration minHoldingPeriod;              // Menor tempo em posição
    private LocalDateTime firstTradeDate;           // Data do primeiro trade
    private LocalDateTime lastTradeDate;            // Data do último trade
    private Duration totalActiveTime;               // Tempo total ativo
    
    // Best/Worst Trades
    private BigDecimal bestTrade;                   // Melhor trade (maior lucro)
    private BigDecimal worstTrade;                  // Pior trade (maior prejuízo)
    private BigDecimal bestTradeReturn;             // Melhor retorno percentual
    private BigDecimal worstTradeReturn;            // Pior retorno percentual
    
    // Recent Performance
    private BigDecimal todaysPnL;                   // P&L de hoje
    private BigDecimal weekPnL;                     // P&L da semana
    private BigDecimal monthPnL;                    // P&L do mês
    
    // Portfolio Allocation
    private BigDecimal allocatedCapital;            // Capital alocado para a estratégia
    private BigDecimal utilizedCapital;             // Capital atualmente utilizado
    private Double capitalUtilization;              // Percentual de capital utilizado
    
    // Volatility Metrics
    private BigDecimal volatility;                  // Volatilidade dos retornos
    private BigDecimal beta;                        // Beta em relação ao mercado
    private BigDecimal alpha;                       // Alpha gerado
    
    /**
     * Calcula métricas derivadas automaticamente
     */
    public void calculateDerivedMetrics() {
        // Taxa de acerto
        if (totalTrades != null && totalTrades > 0) {
            if (winningTrades != null) {
                this.winRate = winningTrades.doubleValue() / totalTrades.doubleValue();
            }
            if (losingTrades != null) {
                this.lossRate = losingTrades.doubleValue() / totalTrades.doubleValue();
            }
        }
        
        // Profit Factor
        if (avgLoss != null && avgLoss.compareTo(BigDecimal.ZERO) != 0 && avgWin != null) {
            BigDecimal totalWins = avgWin.multiply(BigDecimal.valueOf(winningTrades != null ? winningTrades : 0));
            BigDecimal totalLosses = avgLoss.abs().multiply(BigDecimal.valueOf(losingTrades != null ? losingTrades : 0));
            
            if (totalLosses.compareTo(BigDecimal.ZERO) > 0) {
                this.profitFactor = totalWins.divide(totalLosses, 4, java.math.RoundingMode.HALF_UP);
            }
        }
        
        // P&L médio
        if (totalTrades != null && totalTrades > 0 && totalPnL != null) {
            this.avgPnL = totalPnL.divide(BigDecimal.valueOf(totalTrades), 4, java.math.RoundingMode.HALF_UP);
        }
        
        // Calmar Ratio
        if (maxDrawdown != null && maxDrawdown.compareTo(BigDecimal.ZERO) > 0 && totalReturn != null) {
            this.calmarRatio = totalReturn.divide(maxDrawdown.abs(), 4, java.math.RoundingMode.HALF_UP);
        }
        
        // Utilização de capital
        if (allocatedCapital != null && allocatedCapital.compareTo(BigDecimal.ZERO) > 0 && utilizedCapital != null) {
            this.capitalUtilization = utilizedCapital.divide(allocatedCapital, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).doubleValue();
        }
    }
    
    /**
     * Verifica se a estratégia está performando bem
     */
    public boolean isPerformingWell() {
        return totalPnL != null && totalPnL.compareTo(BigDecimal.ZERO) > 0 &&
               winRate != null && winRate > 0.5 &&
               profitFactor != null && profitFactor.compareTo(BigDecimal.ONE) > 0;
    }
    
    /**
     * Calcula o score de performance (0-100)
     */
    public Double getPerformanceScore() {
        double score = 0.0;
        int factors = 0;
        
        // P&L positivo (20 pontos)
        if (totalPnL != null) {
            if (totalPnL.compareTo(BigDecimal.ZERO) > 0) {
                score += 20.0;
            }
            factors++;
        }
        
        // Win rate > 50% (20 pontos)
        if (winRate != null) {
            if (winRate > 0.5) {
                score += 20.0;
            }
            factors++;
        }
        
        // Profit factor > 1.5 (20 pontos)
        if (profitFactor != null) {
            if (profitFactor.compareTo(BigDecimal.valueOf(1.5)) > 0) {
                score += 20.0;
            }
            factors++;
        }
        
        // Sharpe ratio > 1.0 (20 pontos)
        if (sharpeRatio != null) {
            if (sharpeRatio.compareTo(BigDecimal.ONE) > 0) {
                score += 20.0;
            }
            factors++;
        }
        
        // Drawdown baixo < 10% (20 pontos)
        if (maxDrawdownPercentage != null) {
            if (maxDrawdownPercentage.compareTo(BigDecimal.valueOf(10)) < 0) {
                score += 20.0;
            }
            factors++;
        }
        
        return factors > 0 ? score : null;
    }
    
    /**
     * Retorna uma classificação da estratégia
     */
    public String getPerformanceGrade() {
        Double score = getPerformanceScore();
        if (score == null) return "N/A";
        
        if (score >= 90) return "A+";
        if (score >= 80) return "A";
        if (score >= 70) return "B+";
        if (score >= 60) return "B";
        if (score >= 50) return "C+";
        if (score >= 40) return "C";
        if (score >= 30) return "D+";
        if (score >= 20) return "D";
        return "F";
    }
}