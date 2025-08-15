package com.marmitt.ctrade.application.service;

import com.marmitt.ctrade.domain.entity.Order;
import com.marmitt.ctrade.domain.entity.TradingAuditLog;
import com.marmitt.ctrade.domain.entity.TradingPair;
import com.marmitt.ctrade.infrastructure.repository.TradingAuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradingAuditServiceTest {

    @Mock
    private TradingAuditLogRepository auditLogRepository;

    @InjectMocks
    private TradingAuditService auditService;

    private TradingPair tradingPair;
    private Order order;

    @BeforeEach
    void setUp() {
        tradingPair = new TradingPair("BTC", "USD");
        order = new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.BUY, 
                         new BigDecimal("0.1"), new BigDecimal("50000"));
        order.setId("order-123");
    }

    @Test
    void shouldLogOrderPlacement() {
        when(auditLogRepository.save(any(TradingAuditLog.class))).thenReturn(new TradingAuditLog());

        auditService.logOrderPlacement(
            TradingAuditLog.ActionType.PLACE_BUY_ORDER,
            tradingPair,
            Order.OrderType.LIMIT,
            Order.OrderSide.BUY,
            new BigDecimal("0.1"),
            new BigDecimal("50000"),
            order
        );

        verify(auditLogRepository).save(argThat(log -> 
            log.getActionType() == TradingAuditLog.ActionType.PLACE_BUY_ORDER &&
            log.getStatus() == TradingAuditLog.Status.SUCCESS &&
            log.getTradingPair().equals("BTC/USD") &&
            log.getOrderId().equals("order-123") &&
            log.getOrderType() == Order.OrderType.LIMIT &&
            log.getOrderSide() == Order.OrderSide.BUY &&
            log.getQuantity().compareTo(new BigDecimal("0.1")) == 0 &&
            log.getPrice().compareTo(new BigDecimal("50000")) == 0 &&
            log.getTotalValue().compareTo(new BigDecimal("5000.0")) == 0
        ));
    }

    @Test
    void shouldLogOrderAction() {
        when(auditLogRepository.save(any(TradingAuditLog.class))).thenReturn(new TradingAuditLog());

        auditService.logOrderAction(
            TradingAuditLog.ActionType.CANCEL_ORDER,
            "order-123",
            order
        );

        verify(auditLogRepository).save(argThat(log -> 
            log.getActionType() == TradingAuditLog.ActionType.CANCEL_ORDER &&
            log.getStatus() == TradingAuditLog.Status.SUCCESS &&
            log.getOrderId().equals("order-123") &&
            log.getTradingPair().equals("BTC/USD")
        ));
    }

    @Test
    void shouldLogPriceQuery() {
        when(auditLogRepository.save(any(TradingAuditLog.class))).thenReturn(new TradingAuditLog());

        auditService.logPriceQuery(tradingPair, new BigDecimal("50000"));

        verify(auditLogRepository).save(argThat(log -> 
            log.getActionType() == TradingAuditLog.ActionType.GET_CURRENT_PRICE &&
            log.getStatus() == TradingAuditLog.Status.SUCCESS &&
            log.getTradingPair().equals("BTC/USD") &&
            log.getPrice().compareTo(new BigDecimal("50000")) == 0
        ));
    }

    @Test
    void shouldLogActiveOrdersQuery() {
        when(auditLogRepository.save(any(TradingAuditLog.class))).thenReturn(new TradingAuditLog());

        auditService.logActiveOrdersQuery(5);

        verify(auditLogRepository).save(argThat(log -> 
            log.getActionType() == TradingAuditLog.ActionType.GET_ACTIVE_ORDERS &&
            log.getStatus() == TradingAuditLog.Status.SUCCESS
        ));
    }

    @Test
    void shouldLogValidationError() {
        when(auditLogRepository.save(any(TradingAuditLog.class))).thenReturn(new TradingAuditLog());

        auditService.logValidationError(
            TradingAuditLog.ActionType.PLACE_BUY_ORDER,
            "Quantity must be positive",
            tradingPair,
            new BigDecimal("-1"),
            new BigDecimal("50000")
        );

        verify(auditLogRepository).save(argThat(log -> 
            log.getActionType() == TradingAuditLog.ActionType.PLACE_BUY_ORDER &&
            log.getStatus() == TradingAuditLog.Status.VALIDATION_ERROR &&
            log.getErrorMessage().equals("Quantity must be positive") &&
            log.getTradingPair().equals("BTC/USD") &&
            log.getQuantity().compareTo(new BigDecimal("-1")) == 0 &&
            log.getPrice().compareTo(new BigDecimal("50000")) == 0
        ));
    }

    @Test
    void shouldLogError() {
        when(auditLogRepository.save(any(TradingAuditLog.class))).thenReturn(new TradingAuditLog());

        auditService.logError(
            TradingAuditLog.ActionType.PLACE_BUY_ORDER,
            "Exchange connection failed",
            tradingPair,
            "order-123"
        );

        verify(auditLogRepository).save(argThat(log -> 
            log.getActionType() == TradingAuditLog.ActionType.PLACE_BUY_ORDER &&
            log.getStatus() == TradingAuditLog.Status.ERROR &&
            log.getErrorMessage().equals("Exchange connection failed") &&
            log.getTradingPair().equals("BTC/USD") &&
            log.getOrderId().equals("order-123")
        ));
    }

    @Test
    void shouldNotThrowWhenRepositoryFails() {
        when(auditLogRepository.save(any(TradingAuditLog.class)))
            .thenThrow(new RuntimeException("Database connection failed"));

        // Should not throw exception even if audit logging fails
        auditService.logPriceQuery(tradingPair, new BigDecimal("50000"));

        verify(auditLogRepository).save(any(TradingAuditLog.class));
    }
}