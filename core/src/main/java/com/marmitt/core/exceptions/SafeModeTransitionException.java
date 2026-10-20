package com.marmitt.core.exceptions;

/**
 * Mudança de nível do Safe Mode recusada pelas regras do Portfolio (T45): descida que pula
 * nível, escalada sem confirmação, gatilho automático acima de HALT ou alteração concorrente.
 * Mapeada para HTTP 409 na borda.
 */
public class SafeModeTransitionException extends RuntimeException implements CodedError {

    private final String code;

    public SafeModeTransitionException(String reason) {
        this("SAFE_MODE_TRANSITION_REFUSED", reason);
    }

    /**
     * @param code {@code REASON_REQUIRED}, {@code CONFIRMATION_REQUIRED}, {@code SAFE_MODE_STEP_TOO_LARGE}
     *             ou {@code SAFE_MODE_TRANSITION_REFUSED}
     */
    public SafeModeTransitionException(String code, String reason) {
        super(reason);
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
