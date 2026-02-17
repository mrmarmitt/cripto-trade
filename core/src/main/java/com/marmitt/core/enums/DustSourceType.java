package com.marmitt.core.enums;

/**
 * Origem do resíduo registrado na DustAccount.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 3.5, Blueprint 9.2.C</a>
 */
public enum DustSourceType {

    /**
     * Resíduo de arredondamento quando a quantidade restante de um lote < minQty da exchange
     */
    ROUNDING_DUST,

    /**
     * Falha na conversão de fee cross-currency (fallback crítico)
     */
    TECHNICAL_DEBT
}
