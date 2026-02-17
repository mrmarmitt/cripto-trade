package com.marmitt.core.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.List;

/**
 * Representa um símbolo de trading (par de moedas).
 * Serializa para JSON como string simples: "BTCUSDT"
 */
public record Symbol(String value) {

    // Quote currencies ordenadas por tamanho (maiores primeiro para evitar match parcial)
    // Ex: "USDT" antes de "USD" para não dar match errado em "BTCUSDT"
    private static final List<String> KNOWN_QUOTE_CURRENCIES = List.of(
            "USDT", "BUSD", "USDC", "TUSD", "FDUSD",  // Stablecoins (4-5 chars)
            "BTC", "ETH", "BNB",                       // Major cryptos (3 chars)
            "USD", "EUR", "GBP", "BRL"                 // Fiat (3 chars)
    );

    public Symbol {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
    }

    /**
     * Factory method para criar Symbol a partir de string (usado pelo Jackson na deserialização)
     */
    @JsonCreator
    public static Symbol of(String symbol) {
        return new Symbol(symbol.toUpperCase());
    }

    /**
     * Retorna o valor para serialização JSON (como string simples)
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Retorna o base asset do par.
     * Suporta formatos: "BTC/USDT" ou "BTCUSDT"
     */
    public String getBaseAsset() {
        // Formato com separador: "BTC/USDT"
        if (value.contains("/")) {
            return value.split("/")[0];
        }

        // Formato Binance: "BTCUSDT" - detectar quote currency e extrair base
        String quote = detectQuoteCurrency();
        if (quote != null) {
            return value.substring(0, value.length() - quote.length());
        }

        // Fallback: retorna o valor completo
        return value;
    }

    /**
     * Retorna o quote asset do par.
     * Suporta formatos: "BTC/USDT" ou "BTCUSDT"
     */
    public String getQuoteAsset() {
        // Formato com separador: "BTC/USDT"
        if (value.contains("/")) {
            String[] parts = value.split("/");
            return parts.length > 1 ? parts[1] : null;
        }

        // Formato Binance: "BTCUSDT" - detectar quote currency
        return detectQuoteCurrency();
    }

    /**
     * Detecta a quote currency em símbolos sem separador (formato Binance)
     */
    private String detectQuoteCurrency() {
        String upperValue = value.toUpperCase();

        for (String quote : KNOWN_QUOTE_CURRENCIES) {
            if (upperValue.endsWith(quote) && upperValue.length() > quote.length()) {
                return quote;
            }
        }

        return null;
    }

    public boolean isPair() {
        return value.contains("/") || detectQuoteCurrency() != null;
    }

    /**
     * Retorna o símbolo normalizado com separador: "BTCUSDT" -> "BTC/USDT"
     */
    public String normalized() {
        if (value.contains("/")) {
            return value;
        }

        String base = getBaseAsset();
        String quote = getQuoteAsset();

        if (base != null && quote != null) {
            return base + "/" + quote;
        }

        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}