package com.marmitt.application.spring.infrastructure.persistence.repository;

import com.marmitt.application.spring.infrastructure.persistence.entity.GlobalBalanceEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.UUID;

@Repository
public interface GlobalBalanceJdbcRepository extends CrudRepository<GlobalBalanceEntity, UUID> {

    /**
     * Reserva capital atomicamente via UPDATE condicional com lock pessimista.
     * <p>
     * Move {@code amount} de {@code available_balance} → {@code reserved_balance}
     * somente se {@code available_balance >= amount}.
     * <p>
     * Retorna {@code 1} se a reserva foi aplicada, {@code 0} se saldo insuficiente.
     * O caller deve interpretar {@code == 1} como sucesso.
     */
    @Modifying
    @Query("""
            UPDATE global_balances
               SET available_balance = available_balance - :amount,
                   reserved_balance  = reserved_balance  + :amount,
                   updated_at        = NOW()
             WHERE portfolio_id = :portfolioId
               AND available_balance >= :amount
            """)
    int reserveAtomic(
            @Param("portfolioId") UUID portfolioId,
            @Param("amount") BigDecimal amount
    );
}
