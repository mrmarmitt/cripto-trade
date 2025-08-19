package com.marmitt.ctrade.infrastructure.repository;

import com.marmitt.ctrade.domain.entity.Trade;
import com.marmitt.ctrade.domain.entity.TradingPair;
import com.marmitt.ctrade.domain.valueobject.TradeStatus;
import com.marmitt.ctrade.domain.valueobject.TradeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {
    
    // Buscar por estratégia
    List<Trade> findByStrategyName(String strategyName);
    
    List<Trade> findByStrategyNameOrderByEntryTimeDesc(String strategyName);
    
    // Buscar por status
    List<Trade> findByStatus(TradeStatus status);
    
    List<Trade> findByStrategyNameAndStatus(String strategyName, TradeStatus status);
    
    // Buscar por par de trading
    List<Trade> findByPair(TradingPair pair);
    
    List<Trade> findByStrategyNameAndPair(String strategyName, TradingPair pair);
    
    // Buscar por tipo de trade
    List<Trade> findByType(TradeType type);
    
    List<Trade> findByStrategyNameAndType(String strategyName, TradeType type);
    
    // Buscar por período
    List<Trade> findByEntryTimeBetween(LocalDateTime startTime, LocalDateTime endTime);
    
    List<Trade> findByStrategyNameAndEntryTimeBetween(
            String strategyName, LocalDateTime startTime, LocalDateTime endTime);
    
    // Trades abertos mais antigos (para FIFO matching)
    @Query("SELECT t FROM Trade t WHERE t.strategyName = :strategyName " +
           "AND t.pair = :pair AND t.status = :status " +
           "ORDER BY t.entryTime ASC")
    List<Trade> findOldestOpenTrades(
            @Param("strategyName") String strategyName,
            @Param("pair") TradingPair pair,
            @Param("status") TradeStatus status);
    
    // Primeiro trade aberto para FIFO
    @Query("SELECT t FROM Trade t WHERE t.strategyName = :strategyName " +
           "AND t.pair = :pair AND t.status = com.marmitt.ctrade.domain.valueobject.TradeStatus.OPEN " +
           "ORDER BY t.entryTime ASC")
    Optional<Trade> findFirstOpenTrade(
            @Param("strategyName") String strategyName,
            @Param("pair") TradingPair pair);
    
    // Contar trades
    Long countByStrategyName(String strategyName);
    
    Long countByStrategyNameAndStatus(String strategyName, TradeStatus status);
    
    @Query("SELECT COUNT(t) FROM Trade t WHERE t.status = com.marmitt.ctrade.domain.valueobject.TradeStatus.OPEN")
    Long countOpenTrades();
    
    // Métricas de P&L
    @Query("SELECT SUM(t.realizedPnL) FROM Trade t WHERE t.strategyName = :strategyName")
    Optional<BigDecimal> sumRealizedPnLByStrategy(@Param("strategyName") String strategyName);
    
    @Query("SELECT SUM(t.unrealizedPnL) FROM Trade t WHERE t.strategyName = :strategyName " +
           "AND (t.status = com.marmitt.ctrade.domain.valueobject.TradeStatus.OPEN OR t.status = com.marmitt.ctrade.domain.valueobject.TradeStatus.PARTIAL_CLOSED)")
    Optional<BigDecimal> sumUnrealizedPnLByStrategy(@Param("strategyName") String strategyName);
    
    @Query("SELECT SUM(t.realizedPnL + t.unrealizedPnL) FROM Trade t WHERE t.strategyName = :strategyName")
    Optional<BigDecimal> sumTotalPnLByStrategy(@Param("strategyName") String strategyName);
    
    // Métricas globais
    @Query("SELECT SUM(t.realizedPnL) FROM Trade t")
    Optional<BigDecimal> sumAllRealizedPnL();
    
    @Query("SELECT SUM(t.unrealizedPnL) FROM Trade t WHERE (t.status = com.marmitt.ctrade.domain.valueobject.TradeStatus.OPEN OR t.status = com.marmitt.ctrade.domain.valueobject.TradeStatus.PARTIAL_CLOSED)")
    Optional<BigDecimal> sumAllUnrealizedPnL();
    
    // Trades vencedores e perdedores
    @Query("SELECT COUNT(t) FROM Trade t WHERE t.strategyName = :strategyName " +
           "AND t.status = com.marmitt.ctrade.domain.valueobject.TradeStatus.CLOSED AND (t.realizedPnL > 0)")
    Long countWinningTrades(@Param("strategyName") String strategyName);
    
    @Query("SELECT COUNT(t) FROM Trade t WHERE t.strategyName = :strategyName " +
           "AND t.status = com.marmitt.ctrade.domain.valueobject.TradeStatus.CLOSED AND (t.realizedPnL < 0)")
    Long countLosingTrades(@Param("strategyName") String strategyName);
    
    // Melhor e pior trade
    @Query("SELECT t FROM Trade t WHERE t.strategyName = :strategyName " +
           "AND t.status = com.marmitt.ctrade.domain.valueobject.TradeStatus.CLOSED " +
           "ORDER BY t.realizedPnL DESC")
    List<Trade> findBestTrades(@Param("strategyName") String strategyName, Pageable pageable);
    
    @Query("SELECT t FROM Trade t WHERE t.strategyName = :strategyName " +
           "AND t.status = com.marmitt.ctrade.domain.valueobject.TradeStatus.CLOSED " +
           "ORDER BY t.realizedPnL ASC")
    List<Trade> findWorstTrades(@Param("strategyName") String strategyName, Pageable pageable);
    
    // Trades recentes
    @Query("SELECT t FROM Trade t ORDER BY t.entryTime DESC")
    List<Trade> findRecentTrades(Pageable pageable);
    
    @Query("SELECT t FROM Trade t WHERE t.strategyName = :strategyName " +
           "ORDER BY t.entryTime DESC")
    List<Trade> findRecentTradesByStrategy(@Param("strategyName") String strategyName, Pageable pageable);
    
    // Busca com filtros para API
    @Query("SELECT t FROM Trade t WHERE " +
           "(:strategyName IS NULL OR t.strategyName = :strategyName) AND " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:startDate IS NULL OR t.entryTime >= :startDate) AND " +
           "(:endDate IS NULL OR t.entryTime <= :endDate)")
    Page<Trade> findTrades(
            @Param("strategyName") String strategyName,
            @Param("status") TradeStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);
    
    // Trades de hoje
    @Query("SELECT t FROM Trade t WHERE t.strategyName = :strategyName " +
           "AND t.entryTime >= :dayStart AND t.entryTime < :dayEnd")
    List<Trade> findTodaysTrades(@Param("strategyName") String strategyName,
                                @Param("dayStart") LocalDateTime dayStart,
                                @Param("dayEnd") LocalDateTime dayEnd);
    
    // P&L de hoje
    @Query("SELECT SUM(t.realizedPnL + t.unrealizedPnL) FROM Trade t WHERE t.strategyName = :strategyName " +
           "AND t.entryTime >= :dayStart AND t.entryTime < :dayEnd")
    Optional<BigDecimal> sumTodaysPnL(@Param("strategyName") String strategyName,
                                    @Param("dayStart") LocalDateTime dayStart,
                                    @Param("dayEnd") LocalDateTime dayEnd);
    
    // P&L da semana
    @Query("SELECT SUM(t.realizedPnL + t.unrealizedPnL) FROM Trade t WHERE t.strategyName = :strategyName " +
           "AND t.entryTime >= :weekStart")
    Optional<BigDecimal> sumWeekPnL(@Param("strategyName") String strategyName, @Param("weekStart") LocalDateTime weekStart);
    
    // P&L do mês
    @Query("SELECT SUM(t.realizedPnL + t.unrealizedPnL) FROM Trade t WHERE t.strategyName = :strategyName " +
           "AND t.entryTime >= :monthStart")
    Optional<BigDecimal> sumMonthPnL(@Param("strategyName") String strategyName, @Param("monthStart") LocalDateTime monthStart);
    
    // Estatísticas de tempo médio
    @Query("SELECT AVG(t.holdingPeriodSeconds) FROM Trade t WHERE t.strategyName = :strategyName " +
           "AND t.status = com.marmitt.ctrade.domain.valueobject.TradeStatus.CLOSED AND t.holdingPeriodSeconds IS NOT NULL")
    Optional<Double> avgHoldingPeriodSeconds(@Param("strategyName") String strategyName);
    
    // Estratégias com trades
    @Query("SELECT DISTINCT t.strategyName FROM Trade t")
    List<String> findDistinctStrategyNames();
    
    // Trades por ordem
    Optional<Trade> findByEntryOrderId(String entryOrderId);
    
    Optional<Trade> findByExitOrderId(String exitOrderId);
    
    // Validação para evitar duplicatas
    boolean existsByEntryOrderId(String entryOrderId);
}