package com.marmitt.ctrade.infrastructure.exchange.binance.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.ctrade.domain.dto.OrderUpdateMessage;
import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.infrastructure.exchange.binance.processor.BinanceStreamProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BinanceStreamParserTest {
    
    @Mock
    private BinanceStreamProcessor tickerProcessor;

    private BinanceStreamParser parser;
    
    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        List<BinanceStreamProcessor> processors = List.of(tickerProcessor);
        parser = new BinanceStreamParser(objectMapper, processors);
    }
    
    @Test
    void shouldParseMultiplexedStreamMessage() {
        // Given
        when(tickerProcessor.canProcess("!ticker@arr")).thenReturn(true);
        
        String multiplexedMessage = """
            {
                "stream": "!ticker@arr",
                "data": [
                    {
                        "e": "24hrTicker",
                        "E": 1640995200000,
                        "s": "BTCUSD",
                        "c": "50000.00"
                    }
                ]
            }
            """;
        
        // When
        parser.parseMessage(multiplexedMessage);
        
        // Then
        verify(tickerProcessor).process(any());
    }
    
    @Test
    void shouldParseSingleStreamMessage() {
        // Given
        when(tickerProcessor.canProcess("!ticker@arr")).thenReturn(true);
        
        String singleStreamMessage = """
            [
                {
                    "e": "24hrTicker",
                    "E": 1640995200000,
                    "s": "BTCUSD",
                    "c": "50000.00"
                }
            ]
            """;
        
        // When
        parser.parseMessage(singleStreamMessage);
        
        // Then
        verify(tickerProcessor).process(any());
    }
    
    @Test
    void shouldHandleUnknownStream() {
        // Given
        String unknownStreamMessage = """
            {
                "stream": "unknown@stream",
                "data": {"test": "data"}
            }
            """;
        
        // When
        parser.parseMessage(unknownStreamMessage);
        
        // Then
        verify(tickerProcessor, never()).process(any());
    }
}