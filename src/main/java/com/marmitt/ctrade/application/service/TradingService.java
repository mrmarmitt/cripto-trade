package com.marmitt.ctrade.application.service;

import com.marmitt.ctrade.domain.entity.Order;
import com.marmitt.ctrade.domain.entity.TradingAuditLog;
import com.marmitt.ctrade.domain.entity.TradingPair;
import com.marmitt.ctrade.domain.port.ExchangePort;
import com.marmitt.ctrade.domain.valueobject.Price;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradingService {

    private final ExchangePort exchangePort;
    private final TradingAuditService auditService;

    public Order placeBuyOrder(TradingPair tradingPair, BigDecimal quantity, BigDecimal price) {
        try {
            validateOrderParameters(quantity, price);
            
            Order order = new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.BUY, quantity, price);
            Order result = exchangePort.placeOrder(order);
            
            auditService.logOrderPlacement(
                TradingAuditLog.ActionType.PLACE_BUY_ORDER, 
                tradingPair, 
                Order.OrderType.LIMIT, 
                Order.OrderSide.BUY, 
                quantity, 
                price, 
                result
            );
            
            return result;
        } catch (IllegalArgumentException e) {
            auditService.logValidationError(
                TradingAuditLog.ActionType.PLACE_BUY_ORDER, 
                e.getMessage(), 
                tradingPair, 
                quantity, 
                price
            );
            throw e;
        } catch (Exception e) {
            auditService.logError(
                TradingAuditLog.ActionType.PLACE_BUY_ORDER, 
                e.getMessage(), 
                tradingPair, 
                null
            );
            throw e;
        }
    }

    public Order placeSellOrder(TradingPair tradingPair, BigDecimal quantity, BigDecimal price) {
        try {
            validateOrderParameters(quantity, price);
            
            Order order = new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.SELL, quantity, price);
            Order result = exchangePort.placeOrder(order);
            
            auditService.logOrderPlacement(
                TradingAuditLog.ActionType.PLACE_SELL_ORDER, 
                tradingPair, 
                Order.OrderType.LIMIT, 
                Order.OrderSide.SELL, 
                quantity, 
                price, 
                result
            );
            
            return result;
        } catch (IllegalArgumentException e) {
            auditService.logValidationError(
                TradingAuditLog.ActionType.PLACE_SELL_ORDER, 
                e.getMessage(), 
                tradingPair, 
                quantity, 
                price
            );
            throw e;
        } catch (Exception e) {
            auditService.logError(
                TradingAuditLog.ActionType.PLACE_SELL_ORDER, 
                e.getMessage(), 
                tradingPair, 
                null
            );
            throw e;
        }
    }

    public Order placeMarketBuyOrder(TradingPair tradingPair, BigDecimal quantity) {
        try {
            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Quantity must be positive");
            }
            
            Price currentPrice = exchangePort.getCurrentPrice(tradingPair);
            Order order = new Order(tradingPair, Order.OrderType.MARKET, Order.OrderSide.BUY, quantity, currentPrice.getValue());
            Order result = exchangePort.placeOrder(order);
            
            auditService.logOrderPlacement(
                TradingAuditLog.ActionType.PLACE_MARKET_BUY_ORDER, 
                tradingPair, 
                Order.OrderType.MARKET, 
                Order.OrderSide.BUY, 
                quantity, 
                currentPrice.getValue(), 
                result
            );
            
            return result;
        } catch (IllegalArgumentException e) {
            auditService.logValidationError(
                TradingAuditLog.ActionType.PLACE_MARKET_BUY_ORDER, 
                e.getMessage(), 
                tradingPair, 
                quantity, 
                null
            );
            throw e;
        } catch (Exception e) {
            auditService.logError(
                TradingAuditLog.ActionType.PLACE_MARKET_BUY_ORDER, 
                e.getMessage(), 
                tradingPair, 
                null
            );
            throw e;
        }
    }

    public Order cancelOrder(String orderId) {
        try {
            if (orderId == null || orderId.trim().isEmpty()) {
                throw new IllegalArgumentException("Order ID cannot be null or empty");
            }
            
            Order result = exchangePort.cancelOrder(orderId);
            
            auditService.logOrderAction(
                TradingAuditLog.ActionType.CANCEL_ORDER, 
                orderId, 
                result
            );
            
            return result;
        } catch (IllegalArgumentException e) {
            auditService.logValidationError(
                TradingAuditLog.ActionType.CANCEL_ORDER, 
                e.getMessage(), 
                null, 
                null, 
                null
            );
            throw e;
        } catch (Exception e) {
            auditService.logError(
                TradingAuditLog.ActionType.CANCEL_ORDER, 
                e.getMessage(), 
                null, 
                orderId
            );
            throw e;
        }
    }

    public Order getOrderStatus(String orderId) {
        try {
            if (orderId == null || orderId.trim().isEmpty()) {
                throw new IllegalArgumentException("Order ID cannot be null or empty");
            }
            
            Order result = exchangePort.getOrderStatus(orderId);
            
            auditService.logOrderAction(
                TradingAuditLog.ActionType.GET_ORDER_STATUS, 
                orderId, 
                result
            );
            
            return result;
        } catch (IllegalArgumentException e) {
            auditService.logValidationError(
                TradingAuditLog.ActionType.GET_ORDER_STATUS, 
                e.getMessage(), 
                null, 
                null, 
                null
            );
            throw e;
        } catch (Exception e) {
            auditService.logError(
                TradingAuditLog.ActionType.GET_ORDER_STATUS, 
                e.getMessage(), 
                null, 
                orderId
            );
            throw e;
        }
    }

    public List<Order> getActiveOrders() {
        try {
            List<Order> result = exchangePort.getActiveOrders();
            
            auditService.logActiveOrdersQuery(result.size());
            
            return result;
        } catch (Exception e) {
            auditService.logError(
                TradingAuditLog.ActionType.GET_ACTIVE_ORDERS, 
                e.getMessage(), 
                null, 
                null
            );
            throw e;
        }
    }

    public Price getCurrentPrice(TradingPair tradingPair) {
        try {
            if (tradingPair == null) {
                throw new IllegalArgumentException("Trading pair cannot be null");
            }
            
            Price result = exchangePort.getCurrentPrice(tradingPair);
            
            auditService.logPriceQuery(tradingPair, result.getValue());
            
            return result;
        } catch (IllegalArgumentException e) {
            auditService.logValidationError(
                TradingAuditLog.ActionType.GET_CURRENT_PRICE, 
                e.getMessage(), 
                tradingPair, 
                null, 
                null
            );
            throw e;
        } catch (Exception e) {
            auditService.logError(
                TradingAuditLog.ActionType.GET_CURRENT_PRICE, 
                e.getMessage(), 
                tradingPair, 
                null
            );
            throw e;
        }
    }

    public void processOrderStatusUpdate(String orderId, Order.OrderStatus newStatus, String reason) {
        try {
            if (orderId == null || orderId.trim().isEmpty()) {
                throw new IllegalArgumentException("Order ID cannot be null or empty");
            }
            if (newStatus == null) {
                throw new IllegalArgumentException("New status cannot be null");
            }
            
            log.info("Processing order status update: {} -> {}", orderId, newStatus);
            
            // Aqui poderia atualizar em um repository local se tivéssemos
            // Por enquanto apenas logamos a atualização
            log.info("Order {} updated to status: {} (reason: {})", orderId, newStatus, reason);
            
        } catch (Exception e) {
            log.error("Error processing order status update for order {}: {}", orderId, e.getMessage(), e);
            throw e;
        }
    }

    private void validateOrderParameters(BigDecimal quantity, BigDecimal price) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
    }
}