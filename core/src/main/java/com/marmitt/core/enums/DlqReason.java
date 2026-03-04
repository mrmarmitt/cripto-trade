package com.marmitt.core.enums;

/**
 * Motivo do encaminhamento de uma execução para a Dead Letter Queue (DLQ).
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 3.5, Blueprint 4.D, 10.3.D</a>
 */
public enum DlqReason {

    /**
     * Runner não encontrado pelo shortCode extraído do clientOrderId
     */
    UNKNOWN_RUNNER,

    /**
     * clientOrderId com formato inválido ou versão desconhecida
     */
    INVALID_FORMAT,

    /**
     * Conflito durante reconciliação (dados inconsistentes entre exchange e DB)
     */
    RECONCILIATION_CONFLICT,

    /**
     * Símbolo do callback não corresponde a nenhum Runner ativo
     */
    UNKNOWN_SYMBOL,

    /**
     * Evento interno com retries esgotados e necessidade de intervencao manual.
     */
    RETRY_EXHAUSTED
}
