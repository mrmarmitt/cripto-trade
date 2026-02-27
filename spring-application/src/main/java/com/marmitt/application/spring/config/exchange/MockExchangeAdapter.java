package com.marmitt.application.spring.config.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.websocket.request.StreamSubscriptionRequest;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ExchangeAdapterPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ExchangeUrlBuilderPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ReceivedMessageProcessorPort;
import com.marmitt.core.ports.outbound.exchange.adapter.SenderMessageProcessorPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPort;
import com.marmitt.mock.adapter.NoOpWebSocketAdapter;
import com.marmitt.mock.config.MockScenarioConfig;
import com.marmitt.mock.processor.MockReceivedMessageProcessor;
import com.marmitt.mock.processor.MockSenderMessageProcessor;
import com.marmitt.mock.simulator.MockOrderExecutionSimulator;

/**
 * Mock Exchange Adapter para simulação de ordens sem conexão real.
 *
 * Características:
 * - Não conecta a WebSocket real (usa NoOpWebSocketAdapter)
 * - Simula execução de ordens localmente (100% sucesso, latência fixa, sem taxas)
 * - Publica eventos de ordem como se fossem respostas reais
 * - Permite testar estratégias com market data real mas sem risco
 */
public class MockExchangeAdapter implements ExchangeAdapterPort {

    private final WebSocketPort webSocketPort;
    private final ReceivedMessageProcessorPort receivedMessageProcessor;
    private final SenderMessageProcessorPort senderMessageProcessor;
    private final ExchangeUrlBuilderPort urlBuilder;

    public MockExchangeAdapter(ObjectMapper objectMapper, EventPublisherPort eventPublisher) {
        // Mock não precisa de WebSocket real
        this.webSocketPort = new NoOpWebSocketAdapter();

        // Processor para deserializar respostas mockadas
        this.receivedMessageProcessor = new MockReceivedMessageProcessor(objectMapper);

        // Processor para simular execução de ordens
        MockOrderExecutionSimulator simulator = new MockOrderExecutionSimulator();
        MockScenarioConfig config = MockScenarioConfig.defaultConfig();
        this.senderMessageProcessor = new MockSenderMessageProcessor(
                eventPublisher,
                objectMapper,
                simulator,
                config
        );

        // Mock não precisa de URL builder (não conecta)
        this.urlBuilder = new NoOpUrlBuilder();
    }

    @Override
    public String getExchangeName() {
        return "MOCK";
    }

    @Override
    public boolean requiresPostConnection() {
        // Mock não precisa de post-connection (não envia subscrições)
        return false;
    }

    @Override
    public WebSocketPort getWebSocketPort() {
        return webSocketPort;
    }

    @Override
    public ReceivedMessageProcessorPort getReceivedMessageProcessor() {
        return receivedMessageProcessor;
    }

    @Override
    public SenderMessageProcessorPort getSenderMessageProcessor() {
        return senderMessageProcessor;
    }

    @Override
    public ExchangeUrlBuilderPort getUrlBuilder() {
        return urlBuilder;
    }

    /**
     * URL Builder que não faz nada (Mock não precisa construir URLs)
     */
    private static class NoOpUrlBuilder implements ExchangeUrlBuilderPort {
        @Override
        public String buildConnectionUrl(StreamSubscriptionRequest parameters) {
            return "mock://localhost";  // URL fictícia
        }
    }
}
