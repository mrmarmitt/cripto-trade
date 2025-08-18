package com.marmitt.ctrade.domain.port;

import java.util.List;

/**
 * Port para prover trading pairs configurados para monitoramento.
 * 
 * Esta interface permite diferentes implementações:
 * - Configuração estática (application.yml)
 * - Banco de dados
 * - APIs externas
 * - Cache distribuído
 */
public interface TradingPairProvider {
    
    /**
     * Retorna a lista de trading pairs que devem ser monitorados.
     * 
     * @return Lista de símbolos de trading pairs (ex: ["BTCUSDT", "ETHUSDT", "ADAUSDT"])
     */
    List<String> getActiveTradingPairs();
    
    /**
     * Verifica se um trading pair específico está ativo para monitoramento.
     * 
     * @param symbol O símbolo do trading pair (ex: "BTCUSDT")
     * @return true se o par está ativo, false caso contrário
     */
    boolean isActiveTradingPair(String symbol);
    
    /**
     * Retorna a lista de trading pairs formatada para a URL de stream da Binance.
     * 
     * @return String formatada como "btcusdt@ticker/ethusdt@ticker/adausdt@ticker"
     */
    String getFormattedStreamList();
}