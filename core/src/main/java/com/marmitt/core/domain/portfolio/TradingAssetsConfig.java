package com.marmitt.core.domain.portfolio;

import com.marmitt.core.enums.AssetType;

import java.util.Set;

/**
 * Configuração para classificação de tipos de ativos
 * Mantém listas controladas de moedas fiat e stablecoins conhecidas
 */
public class TradingAssetsConfig {

    private static final Set<String> SUPPORTED_FIAT_CURRENCIES = Set.of(
        "USD",  // Dólar Americano
        "BRL"  // Real Brasileiro
    );

    private static final Set<String> SUPPORTED_STABLECOINS = Set.of(
        "USDT", // Tether
        "USDC", // USD Coin
        "BUSD" // Binance USD
    );

    private static final Set<String> SUPPORTED_CRIPTO = Set.of(
            "BTC", // Tether
            "ETH" // USD Coin
    );

    public static AssetType classifyAssetType(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency cannot be null or blank");
        }
        
        String upperCurrency = currency.toUpperCase().trim();
        
        if (SUPPORTED_FIAT_CURRENCIES.contains(upperCurrency)) {
            return AssetType.FIAT;
        }
        
        if (SUPPORTED_STABLECOINS.contains(upperCurrency)) {
            return AssetType.STABLECOIN;
        }

        if (SUPPORTED_CRIPTO.contains(upperCurrency)) {
            return AssetType.CRYPTOCURRENCY;
        }

        // Por padrão, assume como cryptocurrency
        return AssetType.NOT_SUPPORTED;
    }
    
    /**
     * Verifica se uma moeda é fiat
     */
    public static boolean isFiatCurrency(String currency) {
        return currency != null && SUPPORTED_FIAT_CURRENCIES.contains(currency.toUpperCase());
    }
    
    /**
     * Verifica se uma moeda é stablecoin
     */
    public static boolean isStableCoin(String currency) {
        if (currency == null) return false;
        
        String upperCurrency = currency.toUpperCase();
        return SUPPORTED_STABLECOINS.contains(upperCurrency) ||
               upperCurrency.endsWith("USD") || 
               upperCurrency.endsWith("EUR");
    }
    
    /**
     * Verifica se uma moeda é cryptocurrency
     */
    public static boolean isCryptoCurrency(String currency) {
        return currency != null && SUPPORTED_CRIPTO.contains(currency.toUpperCase());
    }
    
    /**
     * Retorna todas as moedas fiat suportadas
     */
    public static Set<String> getSupportedFiatCurrencies() {
        return Set.copyOf(SUPPORTED_FIAT_CURRENCIES);
    }
    
    /**
     * Retorna todas as stablecoins suportadas
     */
    public static Set<String> getSupportedStablecoins() {
        return Set.copyOf(SUPPORTED_STABLECOINS);
    }
    
    /**
     * Retorna todas as cryptocurrencies suportadas
     */
    public static Set<String> getSupportedCryptocurrencies() {
        return Set.copyOf(SUPPORTED_CRIPTO);
    }
    
    /**
     * Adiciona validação se uma combinação de ativos é válida para trading
     */
    public static boolean isValidTradingPair(String baseAsset, String quoteAsset) {
        if (baseAsset == null || quoteAsset == null) return false;
        
        // Não pode tradear mesmo ativo
        if (baseAsset.equalsIgnoreCase(quoteAsset)) return false;
        
        // Quote asset geralmente deve ser fiat ou stablecoin
        AssetType quoteType = classifyAssetType(quoteAsset);
        return quoteType == AssetType.FIAT || quoteType == AssetType.STABLECOIN;
    }
}