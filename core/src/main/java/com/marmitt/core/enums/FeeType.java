package com.marmitt.core.enums;

/**
 * Tipo da fee cobrada pela exchange na execução de uma ordem.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 3.5, Blueprint 9.1</a>
 */
public enum FeeType {

    /**
     * Fee de maker — ordem que adiciona liquidez ao order book
     */
    MAKER,

    /**
     * Fee de taker — ordem que remove liquidez do order book
     */
    TAKER,

    /**
     * Tipo de fee desconhecido ou não reportado pela exchange
     */
    UNKNOWN
}
