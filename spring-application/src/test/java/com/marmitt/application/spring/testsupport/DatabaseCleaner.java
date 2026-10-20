package com.marmitt.application.spring.testsupport;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Limpeza do banco entre testes de integracao. Fonte unica do {@code TRUNCATE} dos testes.
 *
 * <p>O contexto Spring e compartilhado entre classes de teste, entao schedulers (drawdown, gatilhos
 * do Safe Mode, arquivamento) e eventos assincronos do MOCK de um teste anterior podem estar com uma
 * transacao aberta durante a limpeza. O {@code TRUNCATE ... CASCADE} pede lock exclusivo em todas as
 * tabelas ligadas a {@code portfolios}; quando a outra transacao segura locks na ordem inversa, o
 * Postgres escolhe a limpeza como vitima do deadlock. A transacao sobrevivente termina em
 * milissegundos, entao repetir a limpeza resolve.
 */
public final class DatabaseCleaner {

    private static final int MAX_ATTEMPTS = 5;
    private static final long BACKOFF_MS = 100;

    private DatabaseCleaner() {
    }

    /**
     * Apaga portfolios (e, em cascata, runners, transacoes, posicoes, saldos, DLQ e snapshots) e o
     * ledger de capital, num unico comando.
     */
    public static void reset(JdbcTemplate jdbcTemplate) {
        for (int attempt = 1; ; attempt++) {
            try {
                jdbcTemplate.execute("TRUNCATE TABLE portfolios, capital_event_ledger CASCADE");
                return;
            } catch (PessimisticLockingFailureException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                sleep(BACKOFF_MS * attempt);
            }
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while cleaning the database", e);
        }
    }
}
