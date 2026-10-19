package com.marmitt.core.exceptions;

/**
 * A estratégia não produziu uma decisão utilizável neste tick (T41): serviço fora, timeout, erro
 * 5xx, resposta fora do contrato ou decisão velha demais. O pipeline trata como HOLD implícito com o
 * desfecho {@code STRATEGY_UNAVAILABLE}.
 */
public class StrategyUnavailableException extends RuntimeException {

    private final Reason reason;

    public StrategyUnavailableException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public StrategyUnavailableException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        /** Serviço não alcançável ou sem resposta dentro do timeout. */
        UNREACHABLE,
        /** Resposta de erro (status fora de 2xx). */
        ERROR_RESPONSE,
        /** Resposta que não respeita o contrato (decisão malformada, faixa inválida). */
        INVALID_RESPONSE,
        /** Decisão que chegou mais velha que o limite configurado. */
        STALE_DECISION
    }
}
