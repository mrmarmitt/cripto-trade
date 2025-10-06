package com.marmitt.core.exceptions;

/**
 * Exception lançada quando uma transição de estado inválida é tentada.
 * 
 * Usada pelo WebSocketConnectionManager para validar transições de ConnectionStatus.
 */
public class IllegalStateTransitionException extends RuntimeException {
    
    public IllegalStateTransitionException(String message) {
        super(message);
    }
    
    public IllegalStateTransitionException(String message, Throwable cause) {
        super(message, cause);
    }
}