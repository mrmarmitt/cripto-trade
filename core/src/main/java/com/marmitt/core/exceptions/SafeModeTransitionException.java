package com.marmitt.core.exceptions;

/**
 * Mudança de nível do Safe Mode recusada pelas regras do Portfolio (T45): descida que pula
 * nível, escalada sem confirmação, gatilho automático acima de HALT ou alteração concorrente.
 * Mapeada para HTTP 409 na borda.
 */
public class SafeModeTransitionException extends RuntimeException {

    public SafeModeTransitionException(String reason) {
        super(reason);
    }
}
