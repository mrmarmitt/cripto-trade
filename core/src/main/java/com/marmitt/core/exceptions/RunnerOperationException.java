package com.marmitt.core.exceptions;

/**
 * Operação de ciclo de vida ou comando de runner recusado pelo estado atual (T49): transição
 * inválida, exchange indisponível, reconciliação incompleta, alvo que não pertence ao runner.
 * Mapeada para HTTP 409 na borda.
 *
 * <p>Estende {@link IllegalStateException}: é a mesma classe de erro que as transições do
 * {@code StrategyRunner} sempre lançaram, agora com tipo próprio para a API.
 */
public class RunnerOperationException extends IllegalStateException implements CodedError {

    /** Transicao de ciclo de vida que o estado atual do runner nao permite. */
    public static final String INVALID_STATE = "RUNNER_INVALID_STATE";

    private final String code;

    public RunnerOperationException(String message) {
        this(INVALID_STATE, message);
    }

    /**
     * @param code ex.: {@code RUNNER_INVALID_STATE}, {@code RUNNER_RECONCILIATION_INCOMPLETE},
     *             {@code EXCHANGE_UNAVAILABLE}, {@code ORDER_NOT_FOUND}, {@code POSITION_NOT_FOUND},
     *             {@code POSITION_LOCKED}, {@code RUNNER_NOT_ACCEPTING_SIGNALS}
     */
    public RunnerOperationException(String code, String message) {
        super(message);
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
