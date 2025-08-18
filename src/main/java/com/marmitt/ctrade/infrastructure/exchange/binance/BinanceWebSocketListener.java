package com.marmitt.ctrade.infrastructure.exchange.binance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.ctrade.domain.dto.OrderUpdateMessage;
import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.domain.strategy.StreamProcessingStrategy;
import com.marmitt.ctrade.infrastructure.exchange.binance.strategy.BinanceStreamProcessingStrategy;
import com.marmitt.ctrade.infrastructure.websocket.AbstractWebSocketListener;
import com.marmitt.ctrade.infrastructure.websocket.WebSocketConnectionHandler;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Consumer;

@Slf4j
public class BinanceWebSocketListener extends AbstractWebSocketListener {

    // Binance-specific dependencies
    private final StreamProcessingStrategy streamProcessingStrategy;
    
    // Message processing callbacks
    private final Consumer<PriceUpdateMessage> onPriceUpdate;
    private final Consumer<OrderUpdateMessage> onOrderUpdate;
    
    /**
     * Construtor para produção - cria strategy real internamente.
     */
    public BinanceWebSocketListener(WebSocketConnectionHandler connectionHandler,
                                    ObjectMapper objectMapper,
                                    Runnable scheduleReconnectionCallback,
                                    Consumer<PriceUpdateMessage> onPriceUpdate,
                                    Consumer<OrderUpdateMessage> onOrderUpdate) {
        super(connectionHandler, scheduleReconnectionCallback);

        // Cria a strategy específica do Binance internamente
        this.streamProcessingStrategy = new BinanceStreamProcessingStrategy(objectMapper);
        this.onPriceUpdate = onPriceUpdate;
        this.onOrderUpdate = onOrderUpdate;
    }
    
    /**
     * Construtor para testes - permite injetar strategy mockada.
     */
    BinanceWebSocketListener(WebSocketConnectionHandler connectionHandler,
                            StreamProcessingStrategy streamProcessingStrategy,
                            Runnable scheduleReconnectionCallback,
                            Consumer<PriceUpdateMessage> onPriceUpdate,
                            Consumer<OrderUpdateMessage> onOrderUpdate) {
        super(connectionHandler, scheduleReconnectionCallback);

        this.streamProcessingStrategy = streamProcessingStrategy;
        this.onPriceUpdate = onPriceUpdate;
        this.onOrderUpdate = onOrderUpdate;
    }
    
    @Override
    protected String getExchangeName() {
        return "BINANCE";
    }
    
    @Override
    protected void processMessage(@NotNull String messageText) {
        // Processa price updates usando a estratégia específica do Binance
        Optional<PriceUpdateMessage> priceUpdateMessage = streamProcessingStrategy.processPriceUpdate(messageText);
        priceUpdateMessage.ifPresent(onPriceUpdate);
        
        // Processa order updates usando a estratégia específica do Binance
        Optional<OrderUpdateMessage> orderUpdateMessage = streamProcessingStrategy.processOrderUpdate(messageText);
        orderUpdateMessage.ifPresent(onOrderUpdate);
    }
}