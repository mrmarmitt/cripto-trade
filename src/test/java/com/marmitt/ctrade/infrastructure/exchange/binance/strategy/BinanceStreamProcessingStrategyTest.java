package com.marmitt.ctrade.infrastructure.exchange.binance.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.ctrade.domain.dto.OrderUpdateMessage;
import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste para validar que BinanceStreamProcessingStrategy processa corretamente
 * diferentes tipos de mensagem sem fazer cast inválido.
 */
class BinanceStreamProcessingStrategyTest {

    private BinanceStreamProcessingStrategy strategy;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        strategy = new BinanceStreamProcessingStrategy(objectMapper);
    }

    @Test
    void shouldProcessTickerStreamAsPriceUpdateOnly() {
        // Given - Mensagem de ticker multiplexado real da Binance
        String tickerMessage = """
            {
              "stream": "!ticker@arr",
              "data": [
                {
                  "e": "24hrTicker",
                  "E": 1755526664503,
                  "s": "ADAUSDC",
                  "p": "-0.06330000",
                  "c": "0.90400000"
                }
              ]
            }
            """;

        // When
        Optional<PriceUpdateMessage> priceResult = strategy.processPriceUpdate(tickerMessage);
        Optional<OrderUpdateMessage> orderResult = strategy.processOrderUpdate(tickerMessage);

        // Then
        assertThat(priceResult).isPresent(); // Deve processar como price update
        assertThat(orderResult).isEmpty();   // NÃO deve processar como order update
    }

    @Test
    void shouldReturnEmptyForUnknownOrderStream() {
        // Given - Stream que teoricamente seria de ordem (não existe ainda)
        String orderMessage = """
            {
              "stream": "userDataStream",
              "data": {
                "e": "executionReport",
                "E": 1755526664503,
                "s": "BTCUSDT"
              }
            }
            """;

        // When
        Optional<OrderUpdateMessage> orderResult = strategy.processOrderUpdate(orderMessage);

        // Then
        assertThat(orderResult).isEmpty(); // Deve retornar empty pois não há processor de ordem configurado
    }

    @Test
    void shouldHandleInvalidJsonGracefully() {
        // Given
        String invalidJson = "invalid json message";

        // When
        Optional<PriceUpdateMessage> priceResult = strategy.processPriceUpdate(invalidJson);
        Optional<OrderUpdateMessage> orderResult = strategy.processOrderUpdate(invalidJson);

        // Then
        assertThat(priceResult).isEmpty();
        assertThat(orderResult).isEmpty();
    }
}