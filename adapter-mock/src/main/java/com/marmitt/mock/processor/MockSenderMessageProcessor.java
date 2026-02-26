package com.marmitt.mock.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.request.MessageRequest;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.exchange.adapter.SenderMessageProcessorPort;
import com.marmitt.mock.simulator.MockOrderExecutionSimulator;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Processor para envio de mensagens do Mock Adapter.
 * Simula execução de ordens localmente sem enviar para exchange real.
 */
@Slf4j
public class MockSenderMessageProcessor implements SenderMessageProcessorPort {

    private static final long SIMULATED_LATENCY_MS = 200L;

    private final EventPublisherPort eventPublisher;
    private final ObjectMapper objectMapper;
    private final MockOrderExecutionSimulator simulator;
    private final UUID mockConnectionId;

    public MockSenderMessageProcessor(
            EventPublisherPort eventPublisher,
            ObjectMapper objectMapper,
            MockOrderExecutionSimulator simulator
    ) {
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.simulator = simulator;
        this.mockConnectionId = UUID.randomUUID(); // ID fixo para conexão mock
    }

    @Override
    public String execute(MessageRequest request) {
        log.debug("Mock sender processing request - Type: {}", request.getClass().getSimpleName());

        // Mock só processa ordens, ignora outros tipos (StreamRequest, etc.)
        if (request instanceof SendOrderRequest orderRequest) {
            return handleOrderRequest(orderRequest);
        }

        // Outros tipos de request são ignorados
        log.debug("Mock ignoring non-order request: {}", request.getClass().getSimpleName());
        return "{\"status\":\"ignored\",\"message\":\"Mock only processes SendOrderRequest\"}";
    }

    /**
     * Processa request de ordem e simula execução assíncrona
     */
    private String handleOrderRequest(SendOrderRequest orderRequest) {
        log.info("Mock processing order - ClientOrderId: {}, Symbol: {}, Side: {}, Quantity: {}",
                orderRequest.getClientOrderId(), orderRequest.getSymbol(),
                orderRequest.getOrderSide(), orderRequest.getQuantity());

        // Simula execução assíncrona (não bloqueia)
        CompletableFuture.runAsync(() -> simulateOrderExecutionAsync(orderRequest))
                .exceptionally(throwable -> {
                    log.error("Error in async order simulation - ClientOrderId: {}, Error: {}",
                            orderRequest.getClientOrderId(), throwable.getMessage(), throwable);
                    return null;
                });

        // Retorna confirmação imediata de que ordem foi "submetida"
        return String.format(
                "{\"status\":\"submitted\",\"clientOrderId\":\"%s\",\"message\":\"Order submitted to mock exchange\"}",
                orderRequest.getClientOrderId()
        );
    }

    /**
     * Simula execução de ordem de forma assíncrona
     */
    private void simulateOrderExecutionAsync(SendOrderRequest orderRequest) {
        try {
            // Simula latência da exchange (200ms fixo)
            Thread.sleep(SIMULATED_LATENCY_MS);

            String orderId = "MOCK_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            OrderDataDto accepted = simulator.simulateAccepted(orderRequest, orderId);
            publishMockOrderResponse(accepted);

            Thread.sleep(SIMULATED_LATENCY_MS);

            OrderDataDto filled = simulator.simulateFilled(orderRequest, orderId);
            publishMockOrderResponse(filled);

            log.info("Mock order simulation completed - ClientOrderId: {}, OrderId: {}, Status: {}",
                    orderRequest.getClientOrderId(), orderId, filled.status());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Mock order simulation interrupted - ClientOrderId: {}",
                    orderRequest.getClientOrderId());
        } catch (Exception e) {
            log.error("Error simulating order execution - ClientOrderId: {}, Error: {}",
                    orderRequest.getClientOrderId(), e.getMessage(), e);
        }
    }

    /**
     * Publica evento de resposta de ordem mockada.
     * Sistema processará este evento como se fosse resposta real de WebSocket.
     */
    private void publishMockOrderResponse(OrderDataDto orderResponse) {
        try {
            // Serializar OrderDataDto para JSON
            String mockResponseJson = objectMapper.writeValueAsString(orderResponse);

            // Criar contexto da mensagem (exchangeName = "MOCK")
            MessageContext context = MessageContext.create("MOCK", mockConnectionId);

            // Criar evento (mesmo tipo usado por WebSocket real)
            // Nota: Assumindo que RawMessageReceivedEvent existe e é usado pelo sistema
            // Se não existir, precisaremos adaptar
            Object event = createRawMessageReceivedEvent(mockResponseJson, context);

            // Publicar evento
            eventPublisher.publishEvent(event);

            log.debug("Mock order response event published - OrderId: {}, ClientOrderId: {}",
                    orderResponse.orderId(), orderResponse.clientOrderId());

        } catch (Exception e) {
            log.error("Failed to publish mock order response - OrderId: {}, Error: {}",
                    orderResponse.orderId(), e.getMessage(), e);
        }
    }

    /**
     * Cria evento RawMessageReceivedEvent.
     * TODO: Verificar se RawMessageReceivedEvent está acessível ou criar alternativa.
     */
    private Object createRawMessageReceivedEvent(String rawMessage, MessageContext context) {
        // Temporariamente retornando um objeto genérico
        // Precisará ser ajustado quando integrar com spring-application
        try {
            Class<?> eventClass = Class.forName("com.marmitt.application.spring.event.RawMessageReceivedEvent");
            return eventClass.getConstructor(Object.class, String.class, MessageContext.class)
                    .newInstance(this, rawMessage, context);
        } catch (Exception e) {
            log.error("Failed to create RawMessageReceivedEvent: {}", e.getMessage());
            throw new RuntimeException("Cannot create event for mock response", e);
        }
    }
}
