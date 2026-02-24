package com.marmitt.application.spring.infrastructure.persistence.repository;

import com.marmitt.application.spring.infrastructure.persistence.entity.RunnerTransactionMatchEntity;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RunnerTransactionMatchJdbcRepository extends CrudRepository<RunnerTransactionMatchEntity, UUID> {

    /**
     * Busca todos os matches de uma transação (compra ou venda).
     */
    @Query("SELECT * FROM runner_transaction_matches WHERE buy_transaction_id = :txId OR sell_transaction_id = :txId ORDER BY created_at")
    List<RunnerTransactionMatchEntity> findByTransactionId(@Param("txId") UUID txId);
}
