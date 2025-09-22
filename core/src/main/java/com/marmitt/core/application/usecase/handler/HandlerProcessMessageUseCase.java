package com.marmitt.core.application.usecase.handler;

import com.marmitt.core.domain.data.MarketData;
import com.marmitt.core.domain.data.OrderData;
import com.marmitt.core.domain.data.ProcessorResponse;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.WebSocketConnectionManager;
import com.marmitt.core.ports.inbound.handler.HandlerProcessMessagePort;
import com.marmitt.core.ports.outbound.ExchangeAdapterPort;
import com.marmitt.core.ports.outbound.listener.OrderUpdateListener;
import com.marmitt.core.ports.outbound.listener.PriceUpdateListener;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.ListenerRepositoryPort;
import com.marmitt.core.ports.outbound.repository.WebSocketConnectionRepositoryPort;
import com.marmitt.core.ports.outbound.websocket.AdapterMessageProcessorPort;

import java.util.Optional;

/**
 * UseCase para processamento de mensagens recebidas de exchanges.
 * Coordena o processamento e notificação de listeners usando o processor apropriado para cada exchange.
 */
public class HandlerProcessMessageUseCase implements HandlerProcessMessagePort {

    private final WebSocketConnectionRepositoryPort connectionRepository;
    private final ExchangeAdapterRepositoryPort exchangeAdapterRepository;
    private final ListenerRepositoryPort listenerRepository;
    
    public HandlerProcessMessageUseCase(WebSocketConnectionRepositoryPort connectionRepository,
                                        ExchangeAdapterRepositoryPort exchangeAdapterRepository,
                                        ListenerRepositoryPort listenerRepository) {
        this.connectionRepository = connectionRepository;
        this.exchangeAdapterRepository = exchangeAdapterRepository;
        this.listenerRepository = listenerRepository;
    }
    
    @Override
    public ProcessingResult<?> execute(String rawMessage, MessageContext context) {
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("Raw message cannot be null or empty");
        }
        
        if (context == null) {
            throw new IllegalArgumentException("Message context cannot be null");
        }
        WebSocketConnectionManager manager = connectionRepository.getConnection(context.exchangeName());
        manager.onMessageReceived();

        try {
            // Busca o processor apropriado para a exchange
            ExchangeAdapterPort adapter = exchangeAdapterRepository.getAdapter(context.exchangeName());
            AdapterMessageProcessorPort messageProcessor = adapter.getMessageProcessor();

            if (messageProcessor == null) {
                return ProcessingResult.error(context.correlationId().toString(), "No processor found for exchange: " + context.exchangeName());
            }
            
            // Processa a mensagem usando o processor da exchange
            ProcessingResult<? extends ProcessorResponse> result =
                    messageProcessor.processMessage(rawMessage, context);
            
            // Notifica listeners apenas se há dados válidos (Success ou Warning)
            // Não notifica em caso de Error, mesmo que tenha dados
            if ((result.isSuccess() || result.isWarning()) && result.getData().isPresent()) {
                ProcessorResponse response = result.getData().get();
                notifyListeners(response);
            } else {
                manager.onMessageError(result.getErrorMessage().orElse("No message error."));
            }
            
            return result;
            
        } catch (Exception e) {
            // Retorna resultado com erro se algo deu errado
            manager.onMessageError(e.getMessage());
            return ProcessingResult.error(context.correlationId().toString(),"Error processing message: " + e.getMessage(), e);
        }
    }
    
    /**
     * Notifica os listeners apropriados baseado no tipo de dados processados.
     * 
     * @param response dados processados
     */
    private void notifyListeners(ProcessorResponse response) {
        if (response instanceof MarketData marketData) {
            notifyPriceUpdate(marketData);
        } else if (response instanceof OrderData orderData) {
            notifyOrderUpdate(orderData);
        }
    }
    
    /**
     * Notifica todos os listeners registrados sobre atualização de preço.
     * 
     * @param marketData dados do mercado para notificar
     */
    private void notifyPriceUpdate(MarketData marketData) {
        if (marketData == null) {
            throw new IllegalArgumentException("MarketData cannot be null");
        }
        
        var listeners = listenerRepository.getAllPriceUpdateListeners();
        
        for (PriceUpdateListener listener : listeners) {
            try {
                listener.onPriceUpdate(marketData);
            } catch (Exception e) {
                // Log error mas não propaga para não interromper outros listeners
                System.err.println("Error notifying PriceUpdateListener " + 
                    listener.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Notifica todos os listeners registrados sobre atualização de ordem.
     * 
     * @param orderData dados da ordem para notificar
     */
    private void notifyOrderUpdate(OrderData orderData) {
        if (orderData == null) {
            throw new IllegalArgumentException("OrderData cannot be null");
        }
        
        var listeners = listenerRepository.getAllOrderUpdateListeners();
        
        for (OrderUpdateListener listener : listeners) {
            try {
                listener.onOrderUpdate(orderData);
            } catch (Exception e) {
                // Log error mas não propaga para não interromper outros listeners
                System.err.println("Error notifying OrderUpdateListener " + 
                    listener.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    }
}