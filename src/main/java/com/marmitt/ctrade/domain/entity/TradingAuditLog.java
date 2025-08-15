package com.marmitt.ctrade.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trading_audit_log")
@Data
@NoArgsConstructor
public class TradingAuditLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "action_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private ActionType actionType;
    
    @Column(name = "trading_pair")
    private String tradingPair;
    
    @Column(name = "order_id")
    private String orderId;
    
    @Column(name = "order_type")
    @Enumerated(EnumType.STRING)
    private Order.OrderType orderType;
    
    @Column(name = "order_side")
    @Enumerated(EnumType.STRING)
    private Order.OrderSide orderSide;
    
    @Column(name = "quantity", precision = 19, scale = 8)
    private BigDecimal quantity;
    
    @Column(name = "price", precision = 19, scale = 8)
    private BigDecimal price;
    
    @Column(name = "total_value", precision = 19, scale = 8)
    private BigDecimal totalValue;
    
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private Status status;
    
    @Column(name = "error_message", length = 1000)
    private String errorMessage;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "user_context")
    private String userContext;
    
    @Column(name = "request_id")
    private String requestId;
    
    public enum ActionType {
        PLACE_BUY_ORDER,
        PLACE_SELL_ORDER,
        PLACE_MARKET_BUY_ORDER,
        CANCEL_ORDER,
        GET_ORDER_STATUS,
        GET_ACTIVE_ORDERS,
        GET_CURRENT_PRICE
    }
    
    public enum Status {
        SUCCESS,
        ERROR,
        VALIDATION_ERROR
    }
    
    public TradingAuditLog(ActionType actionType, Status status) {
        this.actionType = actionType;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }
    
    public static TradingAuditLog success(ActionType actionType) {
        return new TradingAuditLog(actionType, Status.SUCCESS);
    }
    
    public static TradingAuditLog error(ActionType actionType, String errorMessage) {
        TradingAuditLog log = new TradingAuditLog(actionType, Status.ERROR);
        log.setErrorMessage(errorMessage);
        return log;
    }
    
    public static TradingAuditLog validationError(ActionType actionType, String errorMessage) {
        TradingAuditLog log = new TradingAuditLog(actionType, Status.VALIDATION_ERROR);
        log.setErrorMessage(errorMessage);
        return log;
    }
    
    public TradingAuditLog withTradingPair(String tradingPair) {
        this.tradingPair = tradingPair;
        return this;
    }
    
    public TradingAuditLog withOrderId(String orderId) {
        this.orderId = orderId;
        return this;
    }
    
    public TradingAuditLog withOrderDetails(Order.OrderType orderType, Order.OrderSide orderSide, BigDecimal quantity, BigDecimal price) {
        this.orderType = orderType;
        this.orderSide = orderSide;
        this.quantity = quantity;
        this.price = price;
        if (quantity != null && price != null) {
            this.totalValue = quantity.multiply(price);
        }
        return this;
    }
    
    public TradingAuditLog withUserContext(String userContext) {
        this.userContext = userContext;
        return this;
    }
    
    public TradingAuditLog withRequestId(String requestId) {
        this.requestId = requestId;
        return this;
    }
}