package com.marmitt.application.spring.infrastructure.persistence.repository;

import com.marmitt.application.spring.infrastructure.persistence.entity.TransactionEntity;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionJdbcRepository extends CrudRepository<TransactionEntity, UUID> {

    @Query("SELECT * FROM transactions WHERE portfolio_id = :portfolioId ORDER BY requested_at")
    List<TransactionEntity> findByPortfolioId(@Param("portfolioId") UUID portfolioId);

    @Query("SELECT version FROM transactions WHERE id = :id")
    Optional<Long> findVersionById(@Param("id") UUID id);
}
