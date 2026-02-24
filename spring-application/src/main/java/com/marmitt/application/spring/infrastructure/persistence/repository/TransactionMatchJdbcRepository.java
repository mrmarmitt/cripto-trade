package com.marmitt.application.spring.infrastructure.persistence.repository;

import com.marmitt.application.spring.infrastructure.persistence.entity.TransactionMatchEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TransactionMatchJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<TransactionMatchEntity> ROW_MAPPER = (rs, rowNum) -> mapRow(rs);

    public void save(TransactionMatchEntity entity) {
        jdbcTemplate.update(
                """
                INSERT INTO transaction_matches (buy_transaction_id, sell_transaction_id, matched_quantity, created_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (buy_transaction_id, sell_transaction_id)
                DO UPDATE SET matched_quantity = EXCLUDED.matched_quantity
                """,
                entity.getBuyTransactionId(),
                entity.getSellTransactionId(),
                entity.getMatchedQuantity(),
                entity.getCreatedAt() != null ? entity.getCreatedAt() : Instant.now()
        );
    }

    public void saveAll(List<TransactionMatchEntity> entities) {
        for (TransactionMatchEntity entity : entities) {
            save(entity);
        }
    }

    public void deleteBySellTransactionId(UUID sellTransactionId) {
        jdbcTemplate.update(
                "DELETE FROM transaction_matches WHERE sell_transaction_id = ?",
                sellTransactionId
        );
    }

    public List<TransactionMatchEntity> findByPortfolioId(UUID portfolioId) {
        return jdbcTemplate.query(
                """
                SELECT tm.buy_transaction_id, tm.sell_transaction_id, tm.matched_quantity, tm.created_at
                FROM transaction_matches tm
                JOIN transactions t ON t.id = tm.buy_transaction_id
                WHERE t.portfolio_id = ?
                """,
                ROW_MAPPER,
                portfolioId
        );
    }

    private static TransactionMatchEntity mapRow(ResultSet rs) throws SQLException {
        return TransactionMatchEntity.builder()
                .buyTransactionId(rs.getObject("buy_transaction_id", UUID.class))
                .sellTransactionId(rs.getObject("sell_transaction_id", UUID.class))
                .matchedQuantity(rs.getBigDecimal("matched_quantity"))
                .createdAt(rs.getTimestamp("created_at") != null
                        ? rs.getTimestamp("created_at").toInstant()
                        : null)
                .build();
    }
}
