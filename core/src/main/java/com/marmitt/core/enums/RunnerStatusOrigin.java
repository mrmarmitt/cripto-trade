package com.marmitt.core.enums;

/**
 * Quem mudou o estado do runner (T59). Mesmos nomes do {@link SafeModeOrigin}, mais o boot.
 *
 * <ul>
 *   <li>{@code MANUAL}: comando do operador pela API ({@code X-Reason} como motivo).</li>
 *   <li>{@code AUTOMATIC}: gatilho do sistema (rejeicoes seguidas, falha critica de conexao,
 *       arquivamento do runner zerado).</li>
 *   <li>{@code BOOT}: reconciliacao do boot.</li>
 * </ul>
 */
public enum RunnerStatusOrigin {
    MANUAL,
    AUTOMATIC,
    BOOT
}
