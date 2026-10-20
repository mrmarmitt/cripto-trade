package com.marmitt.core.domain.strategy;

/**
 * Resultado da conferencia de identidade da estrategia remota pelo proprio servico ({@code /v1/meta}).
 *
 * <ul>
 *   <li>{@link #PENDING} — ainda nao conferida desde o cadastro, a reabilitacao ou a troca de endereco.</li>
 *   <li>{@link #VERIFIED} — o servico respondeu com a identidade e o contrato esperados.</li>
 *   <li>{@link #FAILED} — servico fora, identidade divergente ou contrato nao suportado; o motivo fica em
 *       {@code lastError} e a conferencia e repetida periodicamente.</li>
 * </ul>
 */
public enum RemoteStrategyVerification {
    PENDING,
    VERIFIED,
    FAILED
}
