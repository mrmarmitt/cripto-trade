package com.marmitt.ctrade.infrastructure.exchange.binance.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.ctrade.domain.dto.OrderUpdateMessage;
import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.infrastructure.exchange.binance.strategy.processor.TickerStreamProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TickerStreamProcessorTest {
    
    private ObjectMapper objectMapper;
    private TickerStreamProcessor processor;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        processor = new TickerStreamProcessor(objectMapper);
    }
    
    @Test
    void shouldRecognizeTickerStreams() {
        assertThat(processor.canProcess("!ticker@arr")).isTrue();
        assertThat(processor.canProcess("btcusdt@ticker")).isTrue();
        assertThat(processor.canProcess("!bookTicker@arr")).isFalse();
    }
    
    @Test
    void shouldProcessTickerDataFromJsonNode() throws Exception {
        // Given
        String tickerJson = """
            [
                {
                    "e": "24hrTicker",
                    "E": 1640995200000,
                    "s": "BTCUSD",
                    "c": "50000.00",
                    "P": "2.50",
                    "p": "1000.00",
                    "w": "49500.00",
                    "x": "49000.00",
                    "Q": "0.01",
                    "b": "49999.00",
                    "B": "1.0",
                    "a": "50001.00",
                    "A": "1.0",
                    "o": "49000.00",
                    "h": "50100.00",
                    "l": "48900.00",
                    "v": "1000.00",
                    "q": "49500000.00",
                    "O": 1640908800000,
                    "C": 1640995200000,
                    "F": 1,
                    "L": 1000,
                    "n": 1000
                }
            ]
            """;
        
        JsonNode jsonNode = objectMapper.readTree(tickerJson);

        // When
        var result = processor.process(jsonNode);
        
        // Then
        assertThat(result).isPresent();
        PriceUpdateMessage priceUpdate = result.get();
        assertThat(priceUpdate.getTradingPair()).isEqualTo("BTCUSD");
        assertThat(priceUpdate.getPrice()).isEqualTo(new BigDecimal("50000.00"));
        assertThat(priceUpdate.getTimestamp()).isNotNull();
    }
    
    @Test
    void shouldReturnCorrectStreamName() {
        assertThat(processor.getStreamName()).isEqualTo("ticker");
    }
    
}