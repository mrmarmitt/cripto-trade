package com.marmitt.core.enums;

/**
 * Quem mudou o nível do Safe Mode (T45).
 *
 * <p>{@code AUTOMATIC} é o gatilho do próprio sistema (drawdown T43, gatilhos da T25) e só pode
 * subir até {@link SafeModeStatus#HALT}: escalar para níveis de ação em massa e retomar são
 * decisões exclusivas do operador (Blueprint 11.1.B.1 e 11.1.C).
 */
public enum SafeModeOrigin {
    MANUAL,
    AUTOMATIC
}
