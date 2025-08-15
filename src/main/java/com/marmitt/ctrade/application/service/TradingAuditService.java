package com.marmitt.ctrade.application.service;

import com.marmitt.ctrade.domain.entity.Order;
import com.marmitt.ctrade.domain.entity.TradingAuditLog;
import com.marmitt.ctrade.domain.entity.TradingPair;
import com.marmitt.ctrade.infrastructure.repository.TradingAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradingAuditService {
    
    private final TradingAuditLogRepository auditLogRepository;
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logOrderPlacement(TradingAuditLog.ActionType actionType, TradingPair tradingPair, 
                                 Order.OrderType orderType, Order.OrderSide orderSide, 
                                 BigDecimal quantity, BigDecimal price, Order result) {
        try {
            TradingAuditLog auditLog = TradingAuditLog.success(actionType)
                    .withTradingPair(tradingPair.getSymbol())
                    .withOrderDetails(orderType, orderSide, quantity, price)
                    .withOrderId(result.getId())
                    .withRequestId(generateRequestId());
            
            auditLogRepository.save(auditLog);
            log.info("Audit log saved: action={}, orderId={}, tradingPair={}", 
                    actionType, result.getId(), tradingPair.getSymbol());
        } catch (Exception e) {
            log.error("Failed to save audit log for order placement", e);
        }
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logOrderAction(TradingAuditLog.ActionType actionType, String orderId, Order result) {
        try {
            TradingAuditLog auditLog = TradingAuditLog.success(actionType)
                    .withOrderId(orderId)
                    .withTradingPair(result.getTradingPair().getSymbol())
                    .withRequestId(generateRequestId());
            
            auditLogRepository.save(auditLog);
            log.info("Audit log saved: action={}, orderId={}", actionType, orderId);
        } catch (Exception e) {
            log.error("Failed to save audit log for order action", e);
        }
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logPriceQuery(TradingPair tradingPair, BigDecimal price) {
        try {
            TradingAuditLog auditLog = TradingAuditLog.success(TradingAuditLog.ActionType.GET_CURRENT_PRICE)
                    .withTradingPair(tradingPair.getSymbol())
                    .withOrderDetails(null, null, null, price)
                    .withRequestId(generateRequestId());
            
            auditLogRepository.save(auditLog);
            log.debug("Price query audit log saved: tradingPair={}, price={}", 
                    tradingPair.getSymbol(), price);
        } catch (Exception e) {
            log.error("Failed to save audit log for price query", e);
        }
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logActiveOrdersQuery(int orderCount) {
        try {
            TradingAuditLog auditLog = TradingAuditLog.success(TradingAuditLog.ActionType.GET_ACTIVE_ORDERS)
                    .withRequestId(generateRequestId());
            
            auditLogRepository.save(auditLog);
            log.debug("Active orders query audit log saved: orderCount={}", orderCount);
        } catch (Exception e) {
            log.error("Failed to save audit log for active orders query", e);
        }
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logValidationError(TradingAuditLog.ActionType actionType, String errorMessage, 
                                  TradingPair tradingPair, BigDecimal quantity, BigDecimal price) {
        try {
            TradingAuditLog auditLog = TradingAuditLog.validationError(actionType, errorMessage)
                    .withRequestId(generateRequestId());
            
            if (tradingPair != null) {
                auditLog.withTradingPair(tradingPair.getSymbol());
            }
            
            if (quantity != null || price != null) {
                auditLog.withOrderDetails(null, null, quantity, price);
            }
            
            auditLogRepository.save(auditLog);
            log.warn("Validation error audit log saved: action={}, error={}", actionType, errorMessage);
        } catch (Exception e) {
            log.error("Failed to save validation error audit log", e);
        }
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logError(TradingAuditLog.ActionType actionType, String errorMessage, 
                        TradingPair tradingPair, String orderId) {
        try {
            TradingAuditLog auditLog = TradingAuditLog.error(actionType, errorMessage)
                    .withRequestId(generateRequestId());
            
            if (tradingPair != null) {
                auditLog.withTradingPair(tradingPair.getSymbol());
            }
            
            if (orderId != null) {
                auditLog.withOrderId(orderId);
            }
            
            auditLogRepository.save(auditLog);
            log.error("Error audit log saved: action={}, error={}", actionType, errorMessage);
        } catch (Exception e) {
            log.error("Failed to save error audit log", e);
        }
    }
    
    private String generateRequestId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}