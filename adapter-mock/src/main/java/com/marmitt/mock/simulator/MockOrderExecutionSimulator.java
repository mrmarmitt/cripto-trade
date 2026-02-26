package com.marmitt.mock.simulator;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Simula execução de ordens para o Mock Adapter.
 * Configuração inicial: 100% de sucesso, latência fixa, sem taxas.
 */
@Slf4j
public class MockOrderExecutionSimulator {

    /**
     * Simula execução de uma ordem com comportamento configurado.
     *
     * Configuração atual (simplificada):
     * - Taxa de sucesso: 100% (sempre FILLED)
     * - Latência: 200ms (fixa)
     * - Taxa de execução: 0% (sem fees)
     * - Slippage: 0 (executa no preço exato solicitado)
     */
    public OrderDataDto simulateExecution(SendOrderRequest request) {
        log.debug("Simulating order execution - ClientOrderId: {}, Symbol: {}, Side: {}, Quantity: {}, Price: {}",
                request.getClientOrderId(), request.getSymbol(), request.getOrderSide(),
                request.getQuantity(), request.getPrice());

        // Gerar ID único para a ordem mockada
        String mockOrderId = generateMockOrderId();

        // Simular ordem totalmente executada (100% sucesso)
        OrderDataDto response = new OrderDataDto(
                mockOrderId,
                request.getClientOrderId(),
                Symbol.of(request.getSymbol()),
                convertOrderSide(request.getOrderSide()),
                convertOrderType(request.getOrderType()),
                request.getQuantity(),
                request.getQuantity(),  // executedQuantity = quantity (100% executado)
                request.getPrice(),
                request.getPrice(),     // executedPrice = price (sem slippage)
                BigDecimal.ZERO,        // fee = 0 (simplificado)
                OrderDataDto.OrderStatus.FILLED,
                null,                   // rejectReason = null (sucesso)
                Instant.now()
        );

        log.info("Order simulated successfully - OrderId: {}, ClientOrderId: {}, Status: FILLED",
                mockOrderId, request.getClientOrderId());

        return response;
    }

    public OrderDataDto simulateAccepted(SendOrderRequest request, String orderId) {
        return new OrderDataDto(
                orderId,
                request.getClientOrderId(),
                Symbol.of(request.getSymbol()),
                convertOrderSide(request.getOrderSide()),
                convertOrderType(request.getOrderType()),
                request.getQuantity(),
                BigDecimal.ZERO,
                request.getPrice(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                OrderDataDto.OrderStatus.NEW,
                null,
                Instant.now()
        );
    }

    public OrderDataDto simulateFilled(SendOrderRequest request, String orderId) {
        return new OrderDataDto(
                orderId,
                request.getClientOrderId(),
                Symbol.of(request.getSymbol()),
                convertOrderSide(request.getOrderSide()),
                convertOrderType(request.getOrderType()),
                request.getQuantity(),
                request.getQuantity(),
                request.getPrice(),
                request.getPrice(),
                BigDecimal.ZERO,
                OrderDataDto.OrderStatus.FILLED,
                null,
                Instant.now()
        );
    }

    /**
     * Gera ID único para ordem mockada
     */
    private String generateMockOrderId() {
        return "MOCK_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Converte OrderSide do request para OrderSide do DTO
     */
    private OrderDataDto.OrderSide convertOrderSide(com.marmitt.core.enums.OrderSide orderSide) {
        return switch (orderSide) {
            case BUY -> OrderDataDto.OrderSide.BUY;
            case SELL -> OrderDataDto.OrderSide.SELL;
        };
    }

    /**
     * Converte OrderType do request para OrderType do DTO
     */
    private OrderDataDto.OrderType convertOrderType(com.marmitt.core.enums.OrderType orderType) {
        return switch (orderType) {
            case MARKET -> OrderDataDto.OrderType.MARKET;
            case LIMIT -> OrderDataDto.OrderType.LIMIT;
            case STOP_LOSS, STOP_LOSS_LIMIT -> OrderDataDto.OrderType.STOP;
            case TAKE_PROFIT, TAKE_PROFIT_LIMIT -> OrderDataDto.OrderType.STOP_LIMIT;
        };
    }
}
