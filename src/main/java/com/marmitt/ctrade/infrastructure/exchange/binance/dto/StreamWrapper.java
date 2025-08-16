package com.marmitt.ctrade.infrastructure.exchange.binance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * DTO que encapsula o formato de resposta dos streams multiplexados da Binance.
 * Quando usando URLs como "/stream?streams=!ticker@arr/!bookTicker@arr",
 * a Binance retorna mensagens neste formato wrapper.
 */
@Data
public class StreamWrapper {
    
    /**
     * Nome do stream que gerou esta mensagem.
     * Exemplos: "!ticker@arr", "!bookTicker@arr", "btcusdt@ticker"
     */
    @JsonProperty("stream")
    private String stream;
    
    /**
     * Dados específicos do stream. O formato varia conforme o tipo de stream:
     * - ticker: array de objetos BinanceTickerMessage
     * - bookTicker: array de objetos BinanceBookTickerMessage  
     * - userData: objeto específico de user data
     */
    @JsonProperty("data")
    private JsonNode data;
}