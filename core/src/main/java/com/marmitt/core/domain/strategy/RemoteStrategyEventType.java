package com.marmitt.core.domain.strategy;

/**
 * Mudancas registradas no historico de uma estrategia remota (T54). Conferencias repetidas com o mesmo
 * resultado nao geram evento novo.
 */
public enum RemoteStrategyEventType {
    REGISTERED,
    ENABLED,
    DISABLED,
    ENDPOINT_CHANGED,
    VERIFIED,
    VERIFICATION_FAILED
}
