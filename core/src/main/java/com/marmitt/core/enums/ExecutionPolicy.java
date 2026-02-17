package com.marmitt.core.enums;

/**
 * Regra de entrada do Runner — como reage a novos sinais quando já há posição aberta.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 3.5, Blueprint 2.B.1</a>
 */
public enum ExecutionPolicy {

    /**
     * Apenas uma posição aberta por vez. Novo sinal de compra é ignorado se já há posição.
     */
    SINGLE,

    /**
     * Permite múltiplas posições simultâneas (long e short) no mesmo ativo.
     */
    HEDGING,

    /**
     * Posição única consolidada — novos sinais ajustam a posição existente.
     */
    NETTING
}
