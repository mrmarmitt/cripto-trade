-- Add version column for optimistic locking in Spring Data JDBC
ALTER TABLE transaction_matches
    ADD COLUMN version BIGINT DEFAULT 0;
