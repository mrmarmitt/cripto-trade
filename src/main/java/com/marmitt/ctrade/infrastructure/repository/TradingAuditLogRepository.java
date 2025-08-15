package com.marmitt.ctrade.infrastructure.repository;

import com.marmitt.ctrade.domain.entity.TradingAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TradingAuditLogRepository extends JpaRepository<TradingAuditLog, Long> {
    
    List<TradingAuditLog> findByOrderIdOrderByCreatedAtDesc(String orderId);
    
    List<TradingAuditLog> findByTradingPairOrderByCreatedAtDesc(String tradingPair);
    
    List<TradingAuditLog> findByActionTypeOrderByCreatedAtDesc(TradingAuditLog.ActionType actionType);
    
    List<TradingAuditLog> findByStatusOrderByCreatedAtDesc(TradingAuditLog.Status status);
    
    @Query("SELECT t FROM TradingAuditLog t WHERE t.createdAt BETWEEN :startDate AND :endDate ORDER BY t.createdAt DESC")
    List<TradingAuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(
            @Param("startDate") LocalDateTime startDate, 
            @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT t FROM TradingAuditLog t WHERE t.status = 'ERROR' ORDER BY t.createdAt DESC")
    List<TradingAuditLog> findAllErrors();
    
    @Query("SELECT COUNT(t) FROM TradingAuditLog t WHERE t.actionType = :actionType AND t.status = 'SUCCESS'")
    Long countSuccessfulActionsByType(@Param("actionType") TradingAuditLog.ActionType actionType);
    
    @Query("SELECT COUNT(t) FROM TradingAuditLog t WHERE t.actionType = :actionType AND t.status = 'ERROR'")
    Long countErrorsByActionType(@Param("actionType") TradingAuditLog.ActionType actionType);
}