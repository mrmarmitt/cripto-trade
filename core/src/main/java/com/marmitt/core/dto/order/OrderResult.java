package com.marmitt.core.dto.order;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.portfolio.Asset;
import com.marmitt.core.enums.TradingAction;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Representa o resultado da execução de uma ordem
 * Contém todos os dados sobre como a ordem foi processada
 */
@Builder
public record OrderResult(
        UUID orderId,
        UUID portfolioId,
        OrderStatus status,           // Status final da execução
        TradingAction executedAction, // Ação executada (BUY/SELL)
        Symbol symbol,               // Par negociado
        BigDecimal requestedQuantity, // Quantidade solicitada
        BigDecimal executedQuantity,  // Quantidade efetivamente executada
        Asset requestedPrice,        // Preço solicitado
        Asset executedPrice,         // Preço efetivamente executado
        Asset totalValue,            // Valor total da transação
        Asset fees,                  // Taxas cobradas
        Asset slippage,              // Diferença entre preço solicitado e executado
        String exchange,             // Exchange onde foi executada (ex: "BINANCE", "MOCK")
        String externalOrderId,      // ID da ordem na exchange
        String strategy,             // Estratégia que originou a ordem
        String reasoning,            // Justificativa original
        String executionMessage,     // Mensagem de execução/erro
        Instant requestedAt,         // Quando foi solicitada
        Instant executedAt,          // Quando foi executada
        Duration executionTime       // Tempo total de execução
) {
    
    /**
     * Status possíveis para uma ordem
     */
    public enum OrderStatus {
        // Estados síncronos (resposta imediata da submissão)
        SUBMITTED,          // Ordem enviada com sucesso à exchange
        ACCEPTED,           // Exchange aceitou a ordem (aguardando execução)
        REJECTED,           // Exchange rejeitou a ordem
        FAILED,             // Falha técnica na submissão
        TIMEOUT,            // Timeout na submissão
        INSUFFICIENT_BALANCE, // Saldo insuficiente
        INVALID_PRICE,      // Preço inválido/fora do range
        MARKET_CLOSED,      // Mercado fechado
        
        // Estados assíncronos (recebidos via callback/listener)
        EXECUTED,           // Executada com sucesso (100%)
        PARTIAL_FILL,       // Parcialmente executada (< 100%)
        CANCELLED,          // Cancelada (manual ou automática)
        EXPIRED             // Expirada (tempo limite atingido)
    }
    
    public OrderResult {
        Objects.requireNonNull(orderId, "Order ID cannot be null");
        Objects.requireNonNull(portfolioId, "Portfolio ID cannot be null");
        Objects.requireNonNull(status, "Status cannot be null");
        Objects.requireNonNull(symbol, "Symbol cannot be null");
        Objects.requireNonNull(requestedQuantity, "Requested quantity cannot be null");
        Objects.requireNonNull(requestedPrice, "Requested price cannot be null");
        Objects.requireNonNull(exchange, "Exchange cannot be null");
        Objects.requireNonNull(strategy, "Strategy cannot be null");
        Objects.requireNonNull(requestedAt, "Requested at cannot be null");
        
        if (requestedQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Requested quantity must be positive");
        }
        
        if (requestedPrice.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Requested price must be positive");
        }
        
        // Validações específicas para ordens executadas
        if (status == OrderStatus.EXECUTED || status == OrderStatus.PARTIAL_FILL) {
            Objects.requireNonNull(executedAction, "Executed action cannot be null for executed orders");
            Objects.requireNonNull(executedQuantity, "Executed quantity cannot be null for executed orders");
            Objects.requireNonNull(executedPrice, "Executed price cannot be null for executed orders");
            Objects.requireNonNull(totalValue, "Total value cannot be null for executed orders");
            Objects.requireNonNull(fees, "Fees cannot be null for executed orders");
            Objects.requireNonNull(executedAt, "Executed at cannot be null for executed orders");
            
            if (executedQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Executed quantity must be positive for executed orders");
            }
            
            if (executedPrice.amount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Executed price must be positive for executed orders");
            }
        }
    }
    
    /**
     * Cria resultado de submissão bem-sucedida (estado síncrono)
     */
    public static OrderResult submitted(OrderRequest request, String exchange, String externalOrderId) {
        Instant now = Instant.now();
        Duration submissionTime = Duration.between(request.requestedAt(), now);
        
        return OrderResult.builder()
            .orderId(request.orderId())
            .portfolioId(request.portfolioId())
            .status(OrderStatus.SUBMITTED)
            .executedAction(null)
            .symbol(request.symbol())
            .requestedQuantity(request.quantity())
            .executedQuantity(null)
            .requestedPrice(request.targetPrice())
            .executedPrice(null)
            .totalValue(null)
            .fees(null)
            .slippage(null)
            .exchange(exchange)
            .externalOrderId(externalOrderId)
            .strategy(request.strategy())
            .reasoning(request.reasoning())
            .executionMessage("Order submitted successfully to exchange")
            .requestedAt(request.requestedAt())
            .executedAt(now)
            .executionTime(submissionTime)
            .build();
    }
    
    /**
     * Cria resultado de aceitação pela exchange (estado síncrono)
     */
    public static OrderResult accepted(OrderRequest request, String exchange, String externalOrderId) {
        Instant now = Instant.now();
        Duration submissionTime = Duration.between(request.requestedAt(), now);
        
        return OrderResult.builder()
            .orderId(request.orderId())
            .portfolioId(request.portfolioId())
            .status(OrderStatus.ACCEPTED)
            .executedAction(null)
            .symbol(request.symbol())
            .requestedQuantity(request.quantity())
            .executedQuantity(null)
            .requestedPrice(request.targetPrice())
            .executedPrice(null)
            .totalValue(null)
            .fees(null)
            .slippage(null)
            .exchange(exchange)
            .externalOrderId(externalOrderId)
            .strategy(request.strategy())
            .reasoning(request.reasoning())
            .executionMessage("Order accepted by exchange, awaiting execution")
            .requestedAt(request.requestedAt())
            .executedAt(now)
            .executionTime(submissionTime)
            .build();
    }
    
    /**
     * Cria resultado de execução completa (estado assíncrono)
     */
    public static OrderResult executed(OrderRequest request, BigDecimal executedQuantity, 
                                    Asset executedPrice, Asset fees, String exchange, 
                                    String externalOrderId) {
        Instant now = Instant.now();
        Asset totalValue = executedPrice.multiply(executedQuantity);
        Asset slippage = calculateSlippage(request.targetPrice(), executedPrice);
        Duration executionTime = Duration.between(request.requestedAt(), now);
        
        return OrderResult.builder()
            .orderId(request.orderId())
            .portfolioId(request.portfolioId())
            .status(OrderStatus.EXECUTED)
            .executedAction(request.action())
            .symbol(request.symbol())
            .requestedQuantity(request.quantity())
            .executedQuantity(executedQuantity)
            .requestedPrice(request.targetPrice())
            .executedPrice(executedPrice)
            .totalValue(totalValue)
            .fees(fees)
            .slippage(slippage)
            .exchange(exchange)
            .externalOrderId(externalOrderId)
            .strategy(request.strategy())
            .reasoning(request.reasoning())
            .executionMessage("Order executed successfully")
            .requestedAt(request.requestedAt())
            .executedAt(now)
            .executionTime(executionTime)
            .build();
    }
    
    /**
     * Cria resultado de falha para ordem rejeitada
     */
    public static OrderResult failure(OrderRequest request, OrderStatus status, String errorMessage, String exchange) {
        Instant now = Instant.now();
        Duration executionTime = Duration.between(request.requestedAt(), now);
        
        return OrderResult.builder()
            .orderId(request.orderId())
            .portfolioId(request.portfolioId())
            .status(status)
            .executedAction(null)
            .symbol(request.symbol())
            .requestedQuantity(request.quantity())
            .executedQuantity(null)
            .requestedPrice(request.targetPrice())
            .executedPrice(null)
            .totalValue(null)
            .fees(null)
            .slippage(null)
            .exchange(exchange)
            .externalOrderId(null)
            .strategy(request.strategy())
            .reasoning(request.reasoning())
            .executionMessage(errorMessage)
            .requestedAt(request.requestedAt())
            .executedAt(now)
            .executionTime(executionTime)
            .build();
    }
    
    /**
     * Cria resultado de execução parcial
     */
    public static OrderResult partialFill(OrderRequest request, BigDecimal executedQuantity,
                                        Asset executedPrice, Asset fees, String exchange,
                                        String externalOrderId) {
        Instant now = Instant.now();
        Asset totalValue = executedPrice.multiply(executedQuantity);
        Asset slippage = calculateSlippage(request.targetPrice(), executedPrice);
        Duration executionTime = Duration.between(request.requestedAt(), now);
        
        return OrderResult.builder()
            .orderId(request.orderId())
            .portfolioId(request.portfolioId())
            .status(OrderStatus.PARTIAL_FILL)
            .executedAction(request.action())
            .symbol(request.symbol())
            .requestedQuantity(request.quantity())
            .executedQuantity(executedQuantity)
            .requestedPrice(request.targetPrice())
            .executedPrice(executedPrice)
            .totalValue(totalValue)
            .fees(fees)
            .slippage(slippage)
            .exchange(exchange)
            .externalOrderId(externalOrderId)
            .strategy(request.strategy())
            .reasoning(request.reasoning())
            .executionMessage(String.format("Partial fill: %.8f/%.8f executed", 
                                           executedQuantity.doubleValue(), 
                                           request.quantity().doubleValue()))
            .requestedAt(request.requestedAt())
            .executedAt(now)
            .executionTime(executionTime)
            .build();
    }
    
    /**
     * Calcula slippage entre preço solicitado e executado
     */
    private static Asset calculateSlippage(Asset requestedPrice, Asset executedPrice) {
        if (!requestedPrice.currency().equals(executedPrice.currency())) {
            throw new IllegalArgumentException("Cannot calculate slippage between different currencies");
        }
        
        BigDecimal difference = executedPrice.amount().subtract(requestedPrice.amount());
        return Asset.of(difference.abs(), requestedPrice.currency());
    }
    
    /**
     * Verifica se a ordem foi aceita/submetida com sucesso (estado síncrono)
     */
    public boolean isAccepted() {
        return status == OrderStatus.SUBMITTED || status == OrderStatus.ACCEPTED;
    }
    
    /**
     * Verifica se a ordem foi executada com sucesso (estado assíncrono)
     */
    public boolean isExecuted() {
        return status == OrderStatus.EXECUTED || status == OrderStatus.PARTIAL_FILL;
    }
    
    /**
     * Verifica se a submissão falhou (estado síncrono)
     */
    public boolean isSubmissionFailed() {
        return status == OrderStatus.REJECTED || 
               status == OrderStatus.FAILED || 
               status == OrderStatus.TIMEOUT ||
               status == OrderStatus.INSUFFICIENT_BALANCE ||
               status == OrderStatus.INVALID_PRICE ||
               status == OrderStatus.MARKET_CLOSED;
    }
    
    /**
     * Verifica se a ordem está aguardando execução
     */
    public boolean isPending() {
        return status == OrderStatus.SUBMITTED || status == OrderStatus.ACCEPTED;
    }
    
    /**
     * Verifica se houve execução parcial
     */
    public boolean isPartialFill() {
        return status == OrderStatus.PARTIAL_FILL;
    }
    
    /**
     * Calcula percentual de preenchimento da ordem
     */
    public BigDecimal getFillPercentage() {
        if (executedQuantity == null) return BigDecimal.ZERO;
        
        return executedQuantity.divide(requestedQuantity, 4, BigDecimal.ROUND_HALF_UP)
                              .multiply(new BigDecimal("100"));
    }
    
    /**
     * Calcula percentual de slippage em relação ao preço solicitado
     */
    public BigDecimal getSlippagePercentage() {
        if (slippage == null || requestedPrice == null) return BigDecimal.ZERO;
        
        return slippage.amount().divide(requestedPrice.amount(), 6, BigDecimal.ROUND_HALF_UP)
                              .multiply(new BigDecimal("100"));
    }
    
    /**
     * Verifica se o slippage está dentro do limite aceitável
     */
    public boolean isSlippageAcceptable(BigDecimal maxSlippagePercentage) {
        return getSlippagePercentage().compareTo(maxSlippagePercentage) <= 0;
    }
}