package com.marmitt.ctrade.infrastructure.exchange.binance;

import com.marmitt.ctrade.domain.dto.OrderUpdateMessage;
import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.infrastructure.exchange.binance.parser.BinanceStreamParser;
import com.marmitt.ctrade.infrastructure.websocket.AbstractWebSocketListener;
import com.marmitt.ctrade.infrastructure.websocket.ReconnectionStrategy;
import com.marmitt.ctrade.infrastructure.websocket.WebSocketCircuitBreaker;
import com.marmitt.ctrade.infrastructure.websocket.WebSocketConnectionHandler;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Consumer;

@Slf4j
public class BinanceWebSocketListener extends AbstractWebSocketListener {
    
    // Binance-specific dependencies
    private final BinanceStreamParser streamParser;
    
    // Message processing callbacks
    private final Consumer<PriceUpdateMessage> onPriceUpdate;
    private final Consumer<OrderUpdateMessage> onOrderUpdate;
    
    public BinanceWebSocketListener(WebSocketConnectionHandler connectionHandler,
                                    BinanceStreamParser streamParser,
                                    ReconnectionStrategy reconnectionStrategy,
                                    WebSocketCircuitBreaker circuitBreaker,
                                    Runnable scheduleReconnectionCallback,
                                    Consumer<PriceUpdateMessage> onPriceUpdate,
                                    Consumer<OrderUpdateMessage> onOrderUpdate) {
        super(connectionHandler, reconnectionStrategy, circuitBreaker, scheduleReconnectionCallback);
        this.streamParser = streamParser;
        this.onPriceUpdate = onPriceUpdate;
        this.onOrderUpdate = onOrderUpdate;
    }
    
    @Override
    protected String getExchangeName() {
        return "BINANCE";
    }
    
    @Override
    protected void processMessage(@NotNull String messageText) {
        // Delegar processamento para o parser strategy específico do Binance
        Optional<PriceUpdateMessage> priceUpdateMessage = streamParser.parseMessage(messageText);
        priceUpdateMessage.ifPresent(onPriceUpdate);
        
        // Aqui poderia processar outros tipos de mensagem do Binance
        // Ex: Order updates, account updates, etc.
    }
}